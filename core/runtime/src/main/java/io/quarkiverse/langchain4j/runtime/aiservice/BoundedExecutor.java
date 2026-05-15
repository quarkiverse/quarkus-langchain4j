package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

/**
 * Decorator that bounds the concurrency of a delegate {@link Executor} using a {@link Semaphore}.
 * <p>
 * Used to apply {@code quarkus.langchain4j.tools.execution.virtual-threads.max-concurrency} to the underlying
 * virtual-thread pool exposed by {@code VirtualThreadsRecorder.getCurrent()}, which is itself unbounded
 * (one virtual thread per task).
 * <p>
 * Tasks submitted while {@code maxConcurrency} permits are already held are blocked at submission until a permit
 * becomes available. This is intentional — on the parallel-tools dispatch path the calling thread is the loop
 * driver feeding {@code CompletableFuture.supplyAsync(executor)}, so blocking a single submission to throttle the
 * total in-flight count is acceptable.
 */
public final class BoundedExecutor implements Executor {

    private final Executor delegate;
    private final Semaphore permits;

    public BoundedExecutor(Executor delegate, int maxConcurrency) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        if (maxConcurrency <= 0) {
            throw new IllegalArgumentException("maxConcurrency must be > 0, was " + maxConcurrency);
        }
        this.delegate = delegate;
        this.permits = new Semaphore(maxConcurrency);
    }

    @Override
    public void execute(Runnable command) {
        if (command == null) {
            throw new NullPointerException("command");
        }
        try {
            permits.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for a permit on bounded executor", e);
        }
        try {
            delegate.execute(() -> {
                try {
                    command.run();
                } finally {
                    permits.release();
                }
            });
        } catch (RuntimeException | Error t) {
            // delegate.execute itself failed (e.g. RejectedExecutionException) — release the permit we just took
            permits.release();
            throw t;
        }
    }
}
