package io.quarkiverse.langchain4j.test.aiservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.runtime.aiservice.BoundedExecutor;

/**
 * Plain JUnit 5 unit coverage for {@link BoundedExecutor}.
 *
 * <p>
 * {@link BoundedExecutor} has no Quarkus dependencies — it is a thin semaphore-bounded decorator around any
 * {@link Executor}. This test exercises its public contract directly:
 *
 * <ul>
 * <li>argument validation in the constructor and {@code execute};</li>
 * <li>the in-flight cap is enforced even under contention (peak observed concurrency never exceeds the configured
 * maximum);</li>
 * <li>permits are released both when a task succeeds and when it throws, so a stuck permit count cannot starve
 * subsequent submissions;</li>
 * <li>a delegate that throws on {@code execute} (e.g. {@link RejectedExecutionException}) does not leak a permit; the
 * exception propagates and the semaphore is restored;</li>
 * <li>an interruption while waiting for a permit surfaces as a {@code RuntimeException} wrapping
 * {@code InterruptedException} and re-asserts the thread's interrupt flag.</li>
 * </ul>
 */
public class BoundedExecutorTest {

    private ExecutorService backing;

    @AfterEach
    void shutdownBacking() {
        if (backing != null) {
            backing.shutdownNow();
        }
    }

    @Test
    void constructorRejectsNullDelegate() {
        assertThatThrownBy(() -> new BoundedExecutor(null, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("delegate");
    }

    @Test
    void constructorRejectsZeroMaxConcurrency() {
        assertThatThrownBy(() -> new BoundedExecutor(Runnable::run, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxConcurrency");
    }

    @Test
    void constructorRejectsNegativeMaxConcurrency() {
        assertThatThrownBy(() -> new BoundedExecutor(Runnable::run, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxConcurrency");
    }

    @Test
    void executeRejectsNullCommand() {
        BoundedExecutor be = new BoundedExecutor(Runnable::run, 1);
        assertThatThrownBy(() -> be.execute(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void happyPathRunsTaskOnDelegate() throws Exception {
        backing = Executors.newSingleThreadExecutor();
        BoundedExecutor be = new BoundedExecutor(backing, 1);
        CountDownLatch ran = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();

        be.execute(() -> {
            threadName.set(Thread.currentThread().getName());
            ran.countDown();
        });

        assertThat(ran.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(threadName.get())
                .as("task must run on a thread provided by the delegate, not the caller's")
                .isNotEqualTo(Thread.currentThread().getName());
    }

    @Test
    void peakInFlightNeverExceedsMaxConcurrency() throws Exception {
        backing = Executors.newCachedThreadPool();
        int maxConcurrency = 2;
        int taskCount = 5;
        BoundedExecutor be = new BoundedExecutor(backing, maxConcurrency);

        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch allDone = new CountDownLatch(taskCount);

        for (int i = 0; i < taskCount; i++) {
            be.execute(() -> {
                try {
                    int now = inFlight.incrementAndGet();
                    peak.accumulateAndGet(now, Math::max);
                    // Hold the permit a short time so that under contention multiple tasks are visible at once.
                    Thread.sleep(100);
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } finally {
                    inFlight.decrementAndGet();
                    allDone.countDown();
                }
            });
        }

        // We can't ever observe more than maxConcurrency entering at once. To make the assertion robust, wait until
        // at least maxConcurrency have entered (proving the cap is reached), then release everyone.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (peak.get() < maxConcurrency && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        release.countDown();
        assertThat(allDone.await(5, TimeUnit.SECONDS)).isTrue();

        assertThat(peak.get())
                .as("peak in-flight tasks must never exceed maxConcurrency=%d", maxConcurrency)
                .isLessThanOrEqualTo(maxConcurrency);
        assertThat(peak.get())
                .as("under contention with %d tasks the cap should actually be reached", taskCount)
                .isEqualTo(maxConcurrency);
    }

    @Test
    void permitReleasedWhenTaskThrows() throws Exception {
        backing = Executors.newCachedThreadPool();
        BoundedExecutor be = new BoundedExecutor(backing, 1);

        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch firstFinished = new CountDownLatch(1);
        be.execute(() -> {
            firstStarted.countDown();
            try {
                throw new RuntimeException("boom from task");
            } finally {
                firstFinished.countDown();
            }
        });

        assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(firstFinished.await(5, TimeUnit.SECONDS)).isTrue();

        // If the permit was not released in the finally, this second submission would block forever; verify it
        // completes in a bounded window.
        CountDownLatch secondDone = new CountDownLatch(1);
        be.execute(secondDone::countDown);
        assertThat(secondDone.await(5, TimeUnit.SECONDS))
                .as("permit must be released when the task throws so subsequent tasks can proceed")
                .isTrue();
    }

    @Test
    void permitReleasedWhenDelegateExecuteThrows() throws Exception {
        Executor rejecting = command -> {
            throw new RejectedExecutionException("delegate refuses");
        };
        BoundedExecutor be = new BoundedExecutor(rejecting, 1);

        assertThatThrownBy(() -> be.execute(() -> {
        }))
                .isInstanceOf(RejectedExecutionException.class)
                .hasMessageContaining("delegate refuses");

        // Reflection on the private Semaphore field is the simplest way to assert the permit was restored without
        // needing to swap the delegate mid-test. The contract under test is "no permit leaks when delegate.execute
        // throws", and we want a direct, deterministic assertion on the visible state.
        Field permitsField = BoundedExecutor.class.getDeclaredField("permits");
        permitsField.setAccessible(true);
        Semaphore permits = (Semaphore) permitsField.get(be);
        assertThat(permits.availablePermits())
                .as("permit must be restored after delegate.execute throws")
                .isEqualTo(1);
    }

    @Test
    void interruptionWhileWaitingForPermitWrapsInRuntimeAndReassertsFlag() throws Exception {
        backing = Executors.newCachedThreadPool();
        BoundedExecutor be = new BoundedExecutor(backing, 1);

        // Hold the single permit forever from a task we control via a barrier.
        CountDownLatch holderStarted = new CountDownLatch(1);
        CountDownLatch releaseHolder = new CountDownLatch(1);
        be.execute(() -> {
            holderStarted.countDown();
            try {
                releaseHolder.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertThat(holderStarted.await(5, TimeUnit.SECONDS)).isTrue();

        // Now start a thread that will try to acquire the second permit and block. We interrupt it and assert the
        // documented behaviour: RuntimeException wrapping InterruptedException, interrupt flag preserved.
        CyclicBarrier ready = new CyclicBarrier(2);
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        AtomicReference<Boolean> interruptedFlag = new AtomicReference<>();
        Thread waiter = new Thread(() -> {
            try {
                ready.await();
                be.execute(() -> {
                });
            } catch (Throwable t) {
                thrown.set(t);
                interruptedFlag.set(Thread.currentThread().isInterrupted());
            }
        }, "bounded-executor-waiter");
        waiter.setDaemon(true);
        waiter.start();

        ready.await(5, TimeUnit.SECONDS);
        // Give the waiter a moment to enter Semaphore.acquire — a tiny sleep is enough since it has no other work.
        Thread.sleep(100);
        waiter.interrupt();
        waiter.join(5_000);
        assertThat(waiter.isAlive()).as("waiter thread must finish after interrupt").isFalse();

        assertThat(thrown.get())
                .as("interruption while waiting for a permit must surface as a RuntimeException")
                .isInstanceOf(RuntimeException.class);
        assertThat(thrown.get().getCause())
                .as("the wrapping RuntimeException must carry the original InterruptedException")
                .isInstanceOf(InterruptedException.class);
        assertThat(interruptedFlag.get())
                .as("BoundedExecutor must re-assert the thread's interrupt flag after catching InterruptedException")
                .isTrue();

        releaseHolder.countDown();
    }
}
