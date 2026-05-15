package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import dev.langchain4j.service.tool.StreamingToolDispatchHook;
import io.smallrye.common.vertx.VertxContext;
import io.vertx.core.Context;

/**
 * Routes the upstream streaming tool batch dispatch off the Vert.x event loop when blocking-tool
 * execution would otherwise run there.
 * <p>
 * The upstream {@code AiServiceStreamingResponseHandler} invokes {@link #dispatch(Supplier)} when
 * its accumulated tool batch is ready: the supplier runs the {@code ToolBatchDispatcher} (serial or
 * parallel via the configured executor), commits tool results to memory, and issues the follow-up
 * streaming chat request. All of those steps run on whichever thread the supplier is invoked on, so
 * if the chat provider delivered {@code onCompleteResponse} on the event loop, every blocking tool
 * in the batch would trip {@code BlockingToolNotAllowedException} from {@code QuarkusToolExecutor}.
 * <p>
 * This hook hops the entire supplier onto a Vert.x worker via {@code Context.executeBlocking} when
 * the calling thread is on the event loop. Off the event loop (worker pool, virtual thread,
 * test-driven thread) it runs inline — no extra hop. The per-tool {@code Executor} wired through
 * {@code AiServices.executeToolsConcurrently} (already wrapped in {@link VertxContextAwareExecutor})
 * is responsible for context propagation when tools fan out concurrently; this hook only worries
 * about the gather thread + follow-up chat call.
 */
public final class QuarkusStreamingToolDispatchHook implements StreamingToolDispatchHook {

    public static final QuarkusStreamingToolDispatchHook INSTANCE = new QuarkusStreamingToolDispatchHook();

    private QuarkusStreamingToolDispatchHook() {
    }

    @Override
    public <T> CompletionStage<T> dispatch(Supplier<T> work) {
        if (!Context.isOnEventLoopThread()) {
            // Off-event-loop fast path. Worker pool / virtual thread / test thread can run the
            // supplier inline; tools are free to block.
            try {
                return CompletableFuture.completedFuture(work.get());
            } catch (Throwable t) {
                CompletableFuture<T> failed = new CompletableFuture<>();
                failed.completeExceptionally(t);
                return failed;
            }
        }
        // On the event loop: hop to a worker via the captured duplicated context so blocking-tool
        // dispatch + follow-up chat call don't run on the EL.
        Context vertxContext = VertxContext.getOrCreateDuplicatedContext();
        CompletableFuture<T> result = new CompletableFuture<>();
        vertxContext.executeBlocking(() -> {
            try {
                result.complete(work.get());
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
            return null;
        });
        return result;
    }
}
