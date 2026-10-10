package io.quarkiverse.langchain4j.runtime.aiservice;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

import org.jboss.logging.Logger;

import io.smallrye.mutiny.Multi;

/**
 * Bridges AI service methods whose Kotlin signature uses Kotlin coroutines (a trailing
 * {@code kotlin.coroutines.Continuation} parameter, i.e. {@code suspend} functions) or the
 * {@code kotlinx.coroutines.flow.Flow} return type to the runtime of this extension.
 * <p>
 * The Kotlin types are accessed reflectively on purpose, so that applications not using Kotlin do not need any
 * Kotlin dependency on the classpath.
 */
public final class KotlinCoroutineSupport {

    private static final Logger log = Logger.getLogger(KotlinCoroutineSupport.class);

    private KotlinCoroutineSupport() {
    }

    /**
     * Executes the task, and either returns its result directly (when the task completed before the caller had to
     * suspend) or arranges for the continuation to be resumed with the result and returns the Kotlin
     * {@code COROUTINE_SUSPENDED} singleton. The task is always executed on the given executor, so the calling
     * coroutine is never blocked.
     */
    static Object suspend(Executor executor, Object continuation, Callable<Object> task) {
        Handoff handoff = new Handoff();
        executor.execute(() -> {
            Object value = null;
            Throwable failure = null;
            try {
                value = task.call();
            } catch (Throwable t) { // NOSONAR we need to catch everything here, including Errors, to resume the coroutine
                failure = t;
            }
            handoff.complete(continuation, value, failure);
        });
        return handoff.committedResult();
    }

    /**
     * Converts the {@link Multi} produced for a {@code Multi}-shaped AI service method into a
     * {@code kotlinx.coroutines.flow.Flow}. Requires {@code org.jetbrains.kotlinx:kotlinx-coroutines-jdk9} on the
     * classpath (which the deployment already validates when a {@code Flow} method is declared).
     */
    static Object toFlow(Multi<?> multi) {
        Method asFlow = KotlinReflections.AS_FLOW;
        if (asFlow == null) {
            throw new IllegalStateException(
                    "AI service methods returning 'kotlinx.coroutines.flow.Flow' require the 'org.jetbrains.kotlinx:kotlinx-coroutines-jdk9' dependency on the classpath");
        }
        try {
            return asFlow.invoke(null, multi);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to convert the reactive stream to a Kotlin Flow", e);
        }
    }

    /**
     * Lets the task and the calling coroutine agree on who is responsible for delivering the result: the worker
     * thread either completes the task before the caller commits to suspending (in which case the caller returns the
     * result directly) or resumes the continuation afterwards.
     */
    private static final class Handoff {

        private static final int RUNNING = 0;
        private static final int COMPLETED = 1;
        private static final int SUSPENDED = 2;

        private int state = RUNNING; // guarded by this
        private Object result;
        private Throwable failure;

        void complete(Object continuation, Object value, Throwable failure) {
            synchronized (this) {
                if (state == RUNNING) {
                    // the caller did not commit to suspending yet, it will return the value directly
                    this.result = value;
                    this.failure = failure;
                    this.state = COMPLETED;
                    return;
                }
            }
            resume(continuation, value, failure);
        }

        Object committedResult() {
            synchronized (this) {
                if (state == COMPLETED) {
                    return resultOrThrow();
                }
                state = SUSPENDED;
                return KotlinReflections.COROUTINE_SUSPENDED;
            }
        }

        private Object resultOrThrow() {
            if (failure != null) {
                throw failure instanceof RuntimeException re ? re : new RuntimeException(failure);
            }
            return result;
        }
    }

    private static void resume(Object continuation, Object value, Throwable failure) {
        try {
            Object result = failure == null ? value : KotlinReflections.CREATE_FAILURE.invoke(null, failure);
            KotlinReflections.RESUME_WITH.invoke(continuation, result);
        } catch (Throwable t) { // NOSONAR the coroutine machinery resumes on another thread, nothing else we can do
            log.error("Unable to resume the Kotlin continuation", t);
        }
    }

    /**
     * Holds the reflective views of the Kotlin classes. Initialized lazily so that applications without Kotlin
     * never need these classes.
     */
    private static final class KotlinReflections {

        private static final Object COROUTINE_SUSPENDED = coroutineSuspended();
        private static final Method RESUME_WITH = resumeWith();
        private static final Method CREATE_FAILURE = createFailure();
        private static final Method AS_FLOW = asFlow();

        private static Object coroutineSuspended() {
            try {
                return Class.forName("kotlin.coroutines.intrinsics.CoroutineSingletons")
                        .getField("COROUTINE_SUSPENDED").get(null);
            } catch (Exception e) {
                throw new IllegalStateException("Unable to access the Kotlin COROUTINE_SUSPENDED singleton", e);
            }
        }

        private static Method resumeWith() {
            try {
                return Class.forName("kotlin.coroutines.Continuation").getMethod("resumeWith", Object.class);
            } catch (Exception e) {
                throw new IllegalStateException("Unable to access kotlin.coroutines.Continuation#resumeWith", e);
            }
        }

        private static Method createFailure() {
            try {
                return Class.forName("kotlin.ResultKt").getMethod("createFailure", Throwable.class);
            } catch (Exception e) {
                throw new IllegalStateException("Unable to access kotlin.ResultKt#createFailure", e);
            }
        }

        private static Method asFlow() {
            try {
                return Class.forName("kotlinx.coroutines.jdk9.ReactiveFlowKt")
                        .getMethod("asFlow", java.util.concurrent.Flow.Publisher.class);
            } catch (ClassNotFoundException e) {
                // reported when a Flow method is actually invoked
                return null;
            } catch (Exception e) {
                throw new IllegalStateException("Unable to access kotlinx.coroutines.jdk9.ReactiveFlowKt#asFlow", e);
            }
        }
    }
}
