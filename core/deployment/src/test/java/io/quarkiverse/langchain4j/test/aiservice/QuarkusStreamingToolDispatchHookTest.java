package io.quarkiverse.langchain4j.test.aiservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.runtime.aiservice.QuarkusStreamingToolDispatchHook;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.common.vertx.VertxContext;
import io.vertx.core.Context;
import io.vertx.core.Vertx;

/**
 * Unit coverage for {@link QuarkusStreamingToolDispatchHook}.
 *
 * <p>
 * The hook has two paths:
 * <ol>
 * <li>Off the event loop (worker pool, virtual thread, plain test thread): run the supplier inline on the caller and
 * return a completed {@link CompletionStage} with either the value or the supplier's thrown exception.</li>
 * <li>On the event loop: hop the supplier to a Vert.x worker via {@code executeBlocking} on a duplicated context, so
 * blocking tool execution and the follow-up streaming chat call don't run on the EL.</li>
 * </ol>
 *
 * <p>
 * QuarkusUnitTest is required to bring up a real Vert.x instance for the event-loop tests.
 */
public class QuarkusStreamingToolDispatchHookTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    @Inject
    Vertx vertx;

    @Test
    void offEventLoopFastPathRunsInline() throws Exception {
        // Plain test thread — no Vert.x context.
        assertThat(Vertx.currentContext()).isNull();

        Thread caller = Thread.currentThread();
        AtomicReference<Thread> supplierThread = new AtomicReference<>();
        CompletionStage<String> stage = QuarkusStreamingToolDispatchHook.INSTANCE.dispatch(() -> {
            supplierThread.set(Thread.currentThread());
            return "ok";
        });

        assertThat(stage.toCompletableFuture().isDone())
                .as("off-event-loop dispatch must return an already-completed stage")
                .isTrue();
        assertThat(stage.toCompletableFuture().get()).isEqualTo("ok");
        assertThat(supplierThread.get())
                .as("supplier must run inline on the caller's thread — no worker hop off-EL")
                .isSameAs(caller);
    }

    @Test
    void offEventLoopExceptionPathPropagatesThrown() throws Exception {
        assertThat(Vertx.currentContext()).isNull();

        IllegalStateException boom = new IllegalStateException("boom");
        CompletionStage<String> stage = QuarkusStreamingToolDispatchHook.INSTANCE.dispatch(() -> {
            throw boom;
        });

        Throwable t = stage.toCompletableFuture().handle((v, err) -> err).get();
        assertThat(t)
                .as("off-EL exception must be the original throwable (no extra wrapping by the hook itself)")
                .isSameAs(boom);
    }

    @Test
    void onEventLoopHopsToWorker() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Thread> elThread = new AtomicReference<>();
        AtomicReference<Thread> supplierThread = new AtomicReference<>();
        AtomicReference<Context> supplierContext = new AtomicReference<>();
        AtomicReference<String> result = new AtomicReference<>();

        vertx.runOnContext(v -> {
            try {
                elThread.set(Thread.currentThread());
                assertThat(Vertx.currentContext()).isNotNull();
                assertThat(Context.isOnEventLoopThread()).isTrue();

                CompletionStage<String> stage = QuarkusStreamingToolDispatchHook.INSTANCE.dispatch(() -> {
                    supplierThread.set(Thread.currentThread());
                    supplierContext.set(Vertx.currentContext());
                    return "hopped";
                });
                stage.whenComplete((value, err) -> {
                    if (err != null) {
                        failure.set(err);
                    } else {
                        result.set(value);
                    }
                    done.countDown();
                });
            } catch (Throwable t) {
                failure.set(t);
                done.countDown();
            }
        });

        assertThat(done.await(10, TimeUnit.SECONDS))
                .as("on-EL dispatch must complete within 10s")
                .isTrue();
        if (failure.get() != null) {
            throw new AssertionError("on-EL dispatch failed", failure.get());
        }

        assertThat(result.get()).isEqualTo("hopped");
        assertThat(supplierThread.get())
                .as("supplier must run on a worker thread, not the event loop")
                .isNotNull()
                .isNotSameAs(elThread.get());
        assertThat(supplierContext.get())
                .as("supplier must run with a non-null Vert.x context")
                .isNotNull();
        assertThat(VertxContext.isDuplicatedContext(supplierContext.get()))
                .as("supplier must run on a duplicated context (executeBlocking on a getOrCreateDuplicatedContext)")
                .isTrue();
    }

    @Test
    void onEventLoopExceptionPropagatesUnchanged() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> dispatched = new AtomicReference<>();
        AtomicReference<Throwable> wiring = new AtomicReference<>();
        IllegalStateException boom = new IllegalStateException("on-el-boom");

        vertx.runOnContext(v -> {
            try {
                CompletionStage<String> stage = QuarkusStreamingToolDispatchHook.INSTANCE.dispatch(() -> {
                    throw boom;
                });
                stage.whenComplete((value, err) -> {
                    dispatched.set(err);
                    done.countDown();
                });
            } catch (Throwable t) {
                wiring.set(t);
                done.countDown();
            }
        });

        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        if (wiring.get() != null) {
            throw new AssertionError("on-EL dispatch wiring failed", wiring.get());
        }

        assertThat(dispatched.get())
                .as("the returned stage must complete exceptionally with the original throwable")
                .isSameAs(boom);
    }
}
