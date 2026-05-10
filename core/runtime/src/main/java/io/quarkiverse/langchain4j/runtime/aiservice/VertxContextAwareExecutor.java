package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.concurrent.Executor;

import io.smallrye.common.vertx.VertxContext;
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
 * <h3>Per-task duplicated context with snapshot-on-fork</h3>
 *
 * For every task submitted to {@link #execute(Runnable)} the decorator forks a <em>fresh</em> duplicated context off
 * the caller's originating context via
 * {@link io.smallrye.common.vertx.VertxContext#createNewDuplicatedContext(Context)} and runs the task on that fresh
 * context. This is essential in parallel mode: if N tool tasks shared one duplicated context concurrently they would
 * also share its {@code localContextData} map, corrupting ArC's CDI request-scope storage, MDC, OpenTelemetry spans,
 * and any extension code that stashes per-request state in
 * {@link io.vertx.core.impl.ContextInternal#localContextData()}. The fresh duplicated context shares the parent's
 * event loop and root context (so re-entry into Vert.x routes the task back to the same loop) but each sibling task
 * gets its own {@code localContextData}. This matches the contract enforced by Quarkus' own request-scope activator
 * and SmallRye's managed executors — every off-thread task sees a distinct duplicated context.
 * <p>
 * If the caller is itself already on a duplicated context, {@code createNewDuplicatedContext} unwraps to the root
 * before duplicating, so we never accumulate nested wrappers across repeated parallel dispatches.
 * <p>
 * <strong>Snapshot-on-fork:</strong> {@code createNewDuplicatedContext} unwraps to the root before duplicating, so the
 * fresh per-task context starts with an EMPTY {@code localContextData}. That would lose the caller's request-scope
 * state — Quarkus's {@code VertxCurrentContextFactory$VertxCurrentContext.get(...)} reads request-scope state from
 * {@code Vertx.currentContext().getLocal(key)} only when the current context is a duplicated context, and bypasses
 * the {@code FastThreadLocal} fallback in that case. So a fresh duplicated context with empty local data would break
 * request-scope visibility for the parallel task. To preserve the original (CDI request scope, MDC, OTel) state, we
 * snapshot the caller's {@code localContextData} into the fresh fork's {@code localContextData} BEFORE dispatch. The
 * snapshot is taken on the calling thread (so the read is consistent), and the fork's map is independent thereafter
 * (so per-task writes do not leak back to the caller or to siblings — that's the original isolation fix).
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
 * {@code Vertx.currentContext()} report the per-task duplicated context for the duration of the task." That is
 * exactly the contract of {@link ContextInternal#dispatch(Runnable)}: it pushes the context as current via
 * {@code beginDispatch()}, runs the runnable synchronously on the calling thread, then restores via
 * {@code endDispatch()}. No re-submission, no thread switch.
 *
 * <h3>Behaviour</h3>
 * <ul>
 * <li>If the caller is not inside a Vert.x context ({@code Vertx.currentContext() == null}), the runnable is forwarded
 * to the delegate as-is.</li>
 * <li>If the caller is inside a Vert.x context, a fresh duplicated context is forked from the caller's context and
 * installed inside the delegate's task via {@link ContextInternal#dispatch(Runnable)}. The caller's
 * {@code localContextData} is snapshotted into the fork on entry so request scope / MDC / OTel are visible, while
 * the fork's own {@code localContextData} stays independent for writes.</li>
 * <li>{@link #execute(Runnable)} is non-blocking: it captures the context, forks per-task, and forwards to the
 * delegate immediately.</li>
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
        ContextInternal capturedInternal = (ContextInternal) capturedCtx;
        // Fork a fresh duplicated context per task so concurrent siblings don't share localContextData (CDI request
        // scope, MDC, OTel spans, etc.). createNewDuplicatedContext unwraps a duplicated parent to its root before
        // duplicating so repeated parallel dispatch doesn't accumulate nested wrappers.
        ContextInternal perTaskCtx = (ContextInternal) VertxContext.createNewDuplicatedContext(capturedCtx);
        // Snapshot-on-fork: copy the caller's localContextData into the fresh fork BEFORE dispatch. The unwrap-then-
        // duplicate path leaves the fresh context with an empty localContextData, which would invalidate Quarkus'
        // duplicated-context-keyed request-scope lookups, MDC, and OTel state inside the parallel task. We take the
        // snapshot on the calling thread so the source map is read consistently; the fork's map stays independent
        // afterwards, preserving sibling isolation for writes.
        perTaskCtx.localContextData().putAll(capturedInternal.localContextData());
        delegate.execute(() -> perTaskCtx.dispatch(command));
    }
}
