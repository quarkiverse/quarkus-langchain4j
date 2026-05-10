package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.concurrent.Executor;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.impl.ContextInternal;

/**
 * Decorator around an {@link Executor} that propagates the caller's Vert.x duplicated context to each task it runs.
 * <p>
 * Without this decorator the parallel-tool executors ({@code ManagedExecutor} and the virtual-thread pool exposed by
 * {@code VirtualThreadsRecorder}) lose Vert.x duplicated-context propagation, which downstream code relies on for
 * request-scoped CDI beans, MDC, OTel context, and the {@code @RunOnVirtualThread} dispatch performed by
 * {@code QuarkusToolExecutor}.
 *
 * <h3>Why {@link ContextInternal#dispatch(Runnable)} and not {@code Context.executeBlocking(...)}</h3>
 *
 * The existing extension idiom ({@link io.quarkiverse.langchain4j.runtime.VertxUtil#runOutEventLoop}) calls
 * {@code ctx.executeBlocking(callable)} when it wants to run blocking work off the event loop.
 * <p>
 * That primitive is wrong here. {@code executeBlocking} <em>submits</em> the callable to a Vert.x worker pool and
 * returns a {@code Future}. If the calling thread is already a virtual thread (or a {@code ManagedExecutor} worker)
 * picked by us via the underlying delegate, {@code executeBlocking} would yank the work back onto the Vert.x worker
 * pool, defeating both the executor selection and the {@code virtual-threads.max-concurrency} bound.
 * <p>
 * What we actually want is "stay on the thread the underlying delegate gave us, but make
 * {@code Vertx.currentContext()} report the captured duplicated context for the duration of the task." That is exactly
 * the contract of {@link ContextInternal#dispatch(Runnable)}: it pushes the context as current via
 * {@code beginDispatch()}, runs the runnable synchronously on the calling thread, then restores via
 * {@code endDispatch()}. No re-submission, no thread switch.
 * <p>
 * (The original design sketch also proposed {@code Vertx} 5's {@code capturedCtx.dispatch(...)} on the public
 * {@code Context} interface; we don't have that on Vert.x 4. The {@code ContextInternal} cast is the supported path.
 * The {@code VertxContextSafetyToggle} mechanism mentioned in some Quarkus codebases lives in mutiny-vertx-core; we
 * don't need it because {@code dispatch} doesn't post tasks across threads — it runs in-place on whichever thread is
 * already executing the underlying task. Vert.x's "single-threaded duplicated context" invariant is preserved as long
 * as the underlying executor isn't actually running multiple tasks on the same context concurrently; for a virtual-
 * thread-per-task or worker-pool delegate that's per-task isolation by design.)
 *
 * <h3>Behaviour</h3>
 * <ul>
 * <li>If the caller is not inside a Vert.x context ({@code Vertx.currentContext() == null}), the runnable is forwarded
 * to the delegate as-is.</li>
 * <li>If the caller is inside a Vert.x context, the captured {@link Context} is re-installed inside the delegate's
 * task via {@link ContextInternal#dispatch(Runnable)} so {@code Vertx.currentContext()} returns that same context
 * for the lifetime of {@code command.run()}.</li>
 * <li>{@link #execute(Runnable)} is non-blocking: it captures the context and forwards to the delegate immediately.</li>
 * </ul>
 */
public final class VertxContextAwareExecutor implements Executor {

    private final Executor delegate;

    public VertxContextAwareExecutor(Executor delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        this.delegate = delegate;
    }

    /**
     * @return the underlying executor this decorator wraps
     */
    public Executor delegate() {
        return delegate;
    }

    @Override
    public void execute(Runnable command) {
        if (command == null) {
            throw new NullPointerException("command");
        }
        Context capturedCtx = Vertx.currentContext();
        if (capturedCtx == null) {
            // No Vert.x context to propagate — fast path
            delegate.execute(command);
            return;
        }
        if (!(capturedCtx instanceof ContextInternal)) {
            // Defensive: every Vert.x 4 context is a ContextInternal in practice, but if a custom shim is in use we
            // still want execution to proceed without the propagation.
            delegate.execute(command);
            return;
        }
        ContextInternal ctxInternal = (ContextInternal) capturedCtx;
        delegate.execute(() -> ctxInternal.dispatch(command));
    }
}
