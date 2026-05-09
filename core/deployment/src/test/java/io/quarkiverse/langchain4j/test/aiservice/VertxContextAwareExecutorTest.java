package io.quarkiverse.langchain4j.test.aiservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
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

/**
 * Phase 0 unit-style coverage for {@link VertxContextAwareExecutor}.
 *
 * <p>
 * Asserts the propagation contract:
 * <ol>
 * <li>{@code Vertx.currentContext()} inside the lambda equals the captured duplicated context.</li>
 * <li>The lambda runs on the underlying delegate's thread (not back on the event loop).</li>
 * <li>MDC entries set on the caller are visible inside the lambda.</li>
 * <li>{@code Vertx.currentContext()} on the caller (after submission) is unchanged — i.e. submission is non-blocking
 * and does not mutate the caller thread's context.</li>
 * </ol>
 *
 * <p>
 * The Vert.x parts run unconditionally; the VT-pool case requires Java 21+ and is gated via
 * {@link Assumptions#assumeTrue(boolean)} per the brief — the wrapper itself compiles on 17, but
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

            assertThat(innerContext.get())
                    .as("Vertx.currentContext() inside the lambda must equal the captured duplicated context")
                    .isNotNull()
                    .isSameAs(dc);

            assertThat(innerMdc.get())
                    .as("MDC entries from the caller must be visible inside the lambda — duplicated-context locals "
                            + "carry MDC across in JBoss Logging / Quarkus's MDC adapter")
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
        assertThat(innerContext.get())
                .as("the captured duplicated context must be installed inside the VT")
                .isSameAs(dc);
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
}
