package io.quarkiverse.langchain4j.test.aiservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

import org.jboss.logging.MDC;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.runtime.aiservice.BoundedExecutor;
import io.quarkiverse.langchain4j.runtime.aiservice.VertxContextAwareExecutor;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.virtual.threads.VirtualThreadsRecorder;
import io.smallrye.common.vertx.VertxContext;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.impl.ContextInternal;

/**
 * Unit-style coverage for {@link VertxContextAwareExecutor}.
 *
 * <p>
 * Asserts the per-task duplicated-context contract:
 * <ol>
 * <li>{@code Vertx.currentContext()} inside the lambda is a fresh duplicated context that shares the caller's root
 * context but is NOT the same instance — sibling tasks must not share {@link Context#localData()}.</li>
 * <li>The lambda runs on the underlying delegate's thread (not back on the event loop).</li>
 * <li>{@code Vertx.currentContext()} on the caller (after submission) is unchanged — i.e. submission is non-blocking
 * and does not mutate the caller thread's context.</li>
 * <li>Concurrent tasks each get an isolated duplicated context with independent {@code localData}.</li>
 * </ol>
 *
 * <p>
 * The Vert.x parts run unconditionally; the VT-pool case requires Java 21+ and is gated via
 * {@link org.junit.jupiter.api.Assumptions#assumeTrue(boolean)} per the brief — the wrapper itself compiles on 17, but
 * {@code VirtualThreadsRecorder.getCurrent()} only returns a real VT executor on 21+.
 */
public class VertxContextAwareExecutorTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    @Inject
    Vertx vertx;

    /**
     * No Vert.x context on the caller. Wrapper must just delegate.
     */
    @Test
    void executesWithoutContextOutsideVertx() throws Exception {
        Executor delegate = Runnable::run;
        VertxContextAwareExecutor wrapper = new VertxContextAwareExecutor(delegate);
        // We are not on a Vert.x context here.
        assertThat(Vertx.currentContext()).isNull();
        AtomicReference<Context> seen = new AtomicReference<>();
        wrapper.execute(() -> seen.set(Vertx.currentContext()));
        assertThat(seen.get()).isNull();
    }

    /**
     * On a duplicated context, the wrapper must propagate the captured context to the delegate's task while
     * keeping execution on the delegate-supplied thread (here: a single platform-thread executor).
     */
    @Test
    void propagatesDuplicatedContextToDelegateThread() throws Exception {
        Context dc = VertxContext.getOrCreateDuplicatedContext(vertx);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        AtomicReference<Thread> callerThread = new AtomicReference<>();
        AtomicReference<Thread> innerThread = new AtomicReference<>();
        AtomicReference<Context> innerContext = new AtomicReference<>();
        AtomicReference<String> innerMdc = new AtomicReference<>();

        // We submit from the duplicated context (i.e. inside an executeBlocking dispatch) so the wrapper sees a
        // non-null Vertx.currentContext() at submission time. Then we use a dedicated platform-thread delegate so
        // we can prove (a) thread != event loop, (b) thread != caller, (c) Vertx.currentContext() is restored.
        java.util.concurrent.ExecutorService delegate = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "vctx-aware-test-delegate");
            t.setDaemon(true);
            return t;
        });
        try {
            VertxContextAwareExecutor wrapper = new VertxContextAwareExecutor(delegate);

            dc.runOnContext(v -> {
                try {
                    callerThread.set(Thread.currentThread());
                    MDC.put("test-key", "test-value");
                    Context capturedAtCaller = Vertx.currentContext();
                    assertThat(capturedAtCaller).isNotNull();
                    assertThat(VertxContext.isDuplicatedContext(capturedAtCaller)).isTrue();

                    wrapper.execute(() -> {
                        try {
                            innerThread.set(Thread.currentThread());
                            innerContext.set(Vertx.currentContext());
                            innerMdc.set((String) MDC.get("test-key"));
                        } catch (Throwable t) {
                            failure.set(t);
                        } finally {
                            latch.countDown();
                        }
                    });

                    // After submission, the caller must still be on the same context — submission is non-blocking
                    // and must not mutate the caller's thread state.
                    assertThat(Vertx.currentContext()).isSameAs(capturedAtCaller);
                } catch (Throwable t) {
                    failure.set(t);
                    latch.countDown();
                }
            });

            assertThat(latch.await(10, TimeUnit.SECONDS)).as("inner runnable must complete in 10s").isTrue();
            if (failure.get() != null) {
                throw new AssertionError("inner runnable failed", failure.get());
            }

            assertThat(innerThread.get())
                    .as("inner runnable must run on the delegate's thread, not the event loop")
                    .isNotNull()
                    .isNotSameAs(callerThread.get());
            assertThat(innerThread.get().getName()).startsWith("vctx-aware-test-delegate");

            // The wrapper forks a fresh duplicated context per task (see VertxContextAwareExecutor#execute), so the
            // inner context is a sibling duplicate of `dc` — not `dc` itself — sharing the same root context.
            assertThat(innerContext.get())
                    .as("Vertx.currentContext() inside the lambda must be a duplicated context")
                    .isNotNull();
            assertThat(VertxContext.isDuplicatedContext(innerContext.get()))
                    .as("the per-task context installed by the wrapper must itself be a duplicated context")
                    .isTrue();
            assertThat(innerContext.get())
                    .as("the per-task context must be a fresh fork, not the caller's duplicated context")
                    .isNotSameAs(dc);
            assertThat(((ContextInternal) innerContext.get()).unwrap())
                    .as("the per-task duplicated context must share the caller's root context (same event loop / "
                            + "thread affinity)")
                    .isSameAs(((ContextInternal) dc).unwrap());

            // Snapshot-on-fork — the wrapper copies the caller's localContextData snapshot into the fresh per-task
            // duplicated context before dispatch, so request-scope / MDC / OTel state set on the caller's context is
            // visible inside the parallel task. The fork's localContextData remains an independent map afterwards
            // (siblings still don't share writes — see parallelTasksGetIsolatedDuplicatedContexts). Quarkus'
            // VertxMDC reads through Vertx.currentContext().getLocal(...), so a fresh duplicated context with empty
            // localContextData would leave MDC.get(...) returning null inside the task and break
            // duplicated-context-keyed request scope lookups. Hence the snapshot.
            assertThat(innerMdc.get())
                    .as("snapshot-on-fork must make caller MDC visible inside the per-task duplicated context")
                    .isEqualTo("test-value");
        } finally {
            delegate.shutdown();
            MDC.remove("test-key");
        }
    }

    /**
     * VT-pool-specific assertions. Skipped on Java 17-20.
     */
    @Test
    void propagatesContextOntoVirtualThread() throws Exception {
        assumeTrue(Runtime.version().feature() >= 21,
                "VirtualThreadsRecorder.getCurrent() only returns a virtual-thread executor on Java 21+");

        Context dc = VertxContext.getOrCreateDuplicatedContext(vertx);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Thread> innerThread = new AtomicReference<>();
        AtomicReference<Context> innerContext = new AtomicReference<>();

        Executor vt = VirtualThreadsRecorder.getCurrent();
        // Match the production wrapping: VT pool -> BoundedExecutor -> VertxContextAwareExecutor
        Executor wrapped = new VertxContextAwareExecutor(new BoundedExecutor(vt, 8));

        dc.runOnContext(v -> {
            try {
                wrapped.execute(() -> {
                    try {
                        innerThread.set(Thread.currentThread());
                        innerContext.set(Vertx.currentContext());
                    } catch (Throwable t) {
                        failure.set(t);
                    } finally {
                        latch.countDown();
                    }
                });
            } catch (Throwable t) {
                failure.set(t);
                latch.countDown();
            }
        });

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        if (failure.get() != null) {
            fail("inner runnable failed", failure.get());
        }
        // Per-task fork: the inner context is a sibling duplicate of `dc` sharing the same root, not `dc` itself.
        assertThat(innerContext.get())
                .as("a duplicated context must be installed inside the VT task")
                .isNotNull();
        assertThat(VertxContext.isDuplicatedContext(innerContext.get())).isTrue();
        assertThat(innerContext.get()).isNotSameAs(dc);
        assertThat(((ContextInternal) innerContext.get()).unwrap())
                .as("per-task duplicated context shares the caller's root")
                .isSameAs(((ContextInternal) dc).unwrap());
        // Java-version-gated: this whole branch only runs on 21+, and Quarkus' VT executor exclusively runs tasks
        // on virtual threads.
        try {
            // Reflective probe to avoid a direct Thread#isVirtual call (Java 21 API), keeping the test source
            // compilable on Java 17.
            Object isVirtual = Thread.class.getMethod("isVirtual").invoke(innerThread.get());
            assertThat(isVirtual).isEqualTo(Boolean.TRUE);
        } catch (NoSuchMethodException nsme) {
            fail("Thread#isVirtual missing although feature() >= 21 — JVM image is unexpected");
        }
    }

    /**
     * Regression coverage for snapshot-on-fork: the caller's {@code localContextData} sentinels (request-scope keys,
     * MDC, OTel state) MUST be visible inside the per-task fork, while writes inside the fork MUST NOT leak back to
     * the caller's context or to sibling forks.
     *
     * <p>
     * Quarkus's {@code VertxCurrentContextFactory$VertxCurrentContext.get(...)} reads request-scope state from
     * {@code Vertx.currentContext().getLocal(key)} only when the current context is a duplicated context, and bypasses
     * the {@code FastThreadLocal} fallback in that case. So a fresh duplicated context with empty
     * {@code localContextData} would silently break request scope inside parallel tool tasks. The snapshot-on-fork
     * semantics exercised here close that gap.
     */
    @Test
    void snapshotOnForkExposesCallerLocalDataButIsolatesWrites() throws Exception {
        Context dc = VertxContext.getOrCreateDuplicatedContext(vertx);

        java.util.concurrent.ExecutorService delegate = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "vctx-aware-snapshot");
            t.setDaemon(true);
            return t;
        });
        try {
            VertxContextAwareExecutor wrapper = new VertxContextAwareExecutor(delegate);

            CountDownLatch innerEntered = new CountDownLatch(1);
            CountDownLatch callerWrotePostFork = new CountDownLatch(1);
            CountDownLatch innerDone = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();

            AtomicReference<Object> seenSentinelInFork = new AtomicReference<>();
            AtomicReference<Object> seenPostForkWriteInFork = new AtomicReference<>();
            AtomicReference<Object> callerSeesForkWrite = new AtomicReference<>();

            dc.runOnContext(v -> {
                try {
                    // Stash a pre-fork sentinel on the caller's duplicated context. snapshot-on-fork must copy this
                    // value into the fresh per-task fork so it's visible there.
                    Vertx.currentContext().putLocal("caller-sentinel", "visible");

                    wrapper.execute(() -> {
                        try {
                            Context fork = Vertx.currentContext();
                            // (1) caller-sentinel set BEFORE submission must be visible in the fork.
                            seenSentinelInFork.set(fork.getLocal("caller-sentinel"));
                            // (2) write a sentinel inside the fork — must not leak back to caller.
                            fork.putLocal("fork-only-key", "fork-only-value");
                            // Signal caller, then wait for caller to write its post-fork-only sentinel.
                            innerEntered.countDown();
                            if (!callerWrotePostFork.await(10, TimeUnit.SECONDS)) {
                                throw new AssertionError("caller didn't signal post-fork write in 10s");
                            }
                            // (3) caller's post-fork write must NOT be visible in the fork (snapshot semantics —
                            // writes that happen on the caller after fork creation must not be observed).
                            seenPostForkWriteInFork.set(fork.getLocal("caller-post-fork-key"));
                        } catch (Throwable t) {
                            failure.compareAndSet(null, t);
                        } finally {
                            innerDone.countDown();
                        }
                    });

                    // Wait until the fork has run its first reads/writes, then write a post-fork sentinel on the
                    // caller. snapshot-on-fork must NOT make this visible in the fork (the snapshot was taken at
                    // submission time).
                    if (!innerEntered.await(10, TimeUnit.SECONDS)) {
                        failure.compareAndSet(null, new AssertionError("inner didn't enter in 10s"));
                        callerWrotePostFork.countDown();
                        return;
                    }
                    Vertx.currentContext().putLocal("caller-post-fork-key", "post-fork-value");
                    callerWrotePostFork.countDown();
                    // After the fork completes (we'll await innerDone outside this lambda), the caller will check
                    // whether the fork's "fork-only-key" leaked back; we capture that read here lazily below.
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                    innerEntered.countDown();
                    callerWrotePostFork.countDown();
                    innerDone.countDown();
                }
            });

            assertThat(innerDone.await(15, TimeUnit.SECONDS)).as("fork must complete in 15s").isTrue();
            if (failure.get() != null) {
                throw new AssertionError("snapshot-on-fork test failed", failure.get());
            }

            // Caller-side observation: the fork's write to "fork-only-key" must NOT have leaked into the caller's
            // duplicated context. We read on the caller's context (dc) to verify.
            CountDownLatch readDone = new CountDownLatch(1);
            dc.runOnContext(v -> {
                try {
                    callerSeesForkWrite.set(Vertx.currentContext().getLocal("fork-only-key"));
                } finally {
                    readDone.countDown();
                }
            });
            assertThat(readDone.await(5, TimeUnit.SECONDS)).isTrue();

            // (1) caller's pre-fork sentinel was visible inside the fork.
            assertThat(seenSentinelInFork.get())
                    .as("snapshot-on-fork must expose caller's localContextData inside the per-task fork")
                    .isEqualTo("visible");
            // (2) fork-only writes do not leak back to the caller's context.
            assertThat(callerSeesForkWrite.get())
                    .as("writes inside the fork must NOT leak back to the caller's localContextData")
                    .isNull();
            // (3) caller writes that happen AFTER the snapshot are not observed in the fork — we want a one-shot
            // copy, not a live view.
            assertThat(seenPostForkWriteInFork.get())
                    .as("snapshot is one-shot — caller writes after fork submission must not be visible in the fork")
                    .isNull();
        } finally {
            delegate.shutdown();
        }
    }

    /**
     * Regression coverage for the per-task duplicated-context contract: when N tasks are submitted concurrently from
     * the same originating context, each must receive its own duplicated context with isolated {@code localData}.
     *
     * <p>
     * Before the fix, every task ran on the <em>same</em> captured duplicated context, so writes to
     * {@code Context#localData()} (CDI request scope, MDC, OTel spans, …) raced against each other and one task could
     * read another task's value. This test stages four tasks at a barrier so they all hold their per-task context
     * simultaneously, then verifies that (a) each task's context is distinct, (b) each task's localData write is only
     * visible to itself, and (c) all per-task contexts share the same root context.
     */
    @Test
    void parallelTasksGetIsolatedDuplicatedContexts() throws Exception {
        Context dc = VertxContext.getOrCreateDuplicatedContext(vertx);

        int n = 4;
        java.util.concurrent.ExecutorService delegate = Executors.newFixedThreadPool(n, r -> {
            Thread t = new Thread(r, "vctx-aware-parallel");
            t.setDaemon(true);
            return t;
        });
        try {
            VertxContextAwareExecutor wrapper = new VertxContextAwareExecutor(delegate);

            CyclicBarrier barrier = new CyclicBarrier(n);
            CountDownLatch done = new CountDownLatch(n);
            List<AtomicReference<Context>> seenContexts = new ArrayList<>();
            List<AtomicReference<Object>> seenLocalReads = new ArrayList<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            for (int i = 0; i < n; i++) {
                seenContexts.add(new AtomicReference<>());
                seenLocalReads.add(new AtomicReference<>());
            }

            dc.runOnContext(v -> {
                for (int i = 0; i < n; i++) {
                    final int idx = i;
                    wrapper.execute(() -> {
                        try {
                            Context cur = Vertx.currentContext();
                            seenContexts.get(idx).set(cur);
                            // Each task writes a unique sentinel into its OWN localData and reads it back AFTER all
                            // siblings have also written. If the wrapper were sharing one duplicated context, the
                            // last writer would clobber every reader and the subsequent get() would return some other
                            // task's value (or null).
                            cur.putLocal("per-task-key", "value-" + idx);
                            barrier.await(10, TimeUnit.SECONDS);
                            seenLocalReads.get(idx).set(cur.getLocal("per-task-key"));
                        } catch (Throwable t) {
                            failure.compareAndSet(null, t);
                        } finally {
                            done.countDown();
                        }
                    });
                }
            });

            assertThat(done.await(15, TimeUnit.SECONDS))
                    .as("all %d parallel tasks must complete in 15s", n).isTrue();
            if (failure.get() != null) {
                throw new AssertionError("a parallel task failed", failure.get());
            }

            // (a) Each per-task context is distinct.
            for (int i = 0; i < n; i++) {
                assertThat(seenContexts.get(i).get())
                        .as("task %d must see a non-null Vertx.currentContext()", i).isNotNull();
                assertThat(VertxContext.isDuplicatedContext(seenContexts.get(i).get()))
                        .as("task %d must run on a duplicated context", i).isTrue();
                for (int j = i + 1; j < n; j++) {
                    assertThat(seenContexts.get(i).get())
                            .as("tasks %d and %d must run on DIFFERENT duplicated contexts so their localData "
                                    + "writes don't collide", i, j)
                            .isNotSameAs(seenContexts.get(j).get());
                }
            }

            // (b) Each task's local write is only visible to itself.
            for (int i = 0; i < n; i++) {
                assertThat(seenLocalReads.get(i).get())
                        .as("task %d must read back its own sentinel value, not a sibling's", i)
                        .isEqualTo("value-" + i);
            }

            // (c) All per-task contexts share the same root context.
            ContextInternal root = ((ContextInternal) dc).unwrap();
            for (int i = 0; i < n; i++) {
                assertThat(((ContextInternal) seenContexts.get(i).get()).unwrap())
                        .as("task %d's duplicated context must share the originating root context", i)
                        .isSameAs(root);
            }
        } finally {
            delegate.shutdown();
        }
    }
}
