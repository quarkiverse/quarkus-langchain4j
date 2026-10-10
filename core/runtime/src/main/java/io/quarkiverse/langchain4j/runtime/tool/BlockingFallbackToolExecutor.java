package io.quarkiverse.langchain4j.runtime.tool;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;

/**
 * Runs a {@link ToolExecutor} that does not implement {@link ToolExecutor#executeAsync} through
 * {@link ToolExecutor#executeWithContext}, on a thread that is allowed to block.
 */
public class BlockingFallbackToolExecutor implements ToolExecutor {

    private final ToolExecutor delegate;

    public BlockingFallbackToolExecutor(ToolExecutor delegate) {
        this.delegate = delegate;
    }

    /**
     * Wraps the executors that are not {@link QuarkusToolExecutor}s, which always implement asynchronous execution.
     */
    public static Map<String, ToolExecutor> wrap(Map<String, ToolExecutor> toolExecutors) {
        Map<String, ToolExecutor> result = new LinkedHashMap<>();
        for (Map.Entry<String, ToolExecutor> entry : toolExecutors.entrySet()) {
            ToolExecutor toolExecutor = entry.getValue();
            result.put(entry.getKey(),
                    toolExecutor instanceof QuarkusToolExecutor ? toolExecutor
                            : new BlockingFallbackToolExecutor(toolExecutor));
        }
        return result;
    }

    @Override
    public String execute(ToolExecutionRequest request, Object memoryId) {
        return delegate.execute(request, memoryId);
    }

    @Override
    public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {
        return delegate.executeWithContext(request, context);
    }

    @Override
    public CompletableFuture<ToolExecutionResult> executeAsync(ToolExecutionRequest request, InvocationContext context) {
        return delegate.executeAsync(request, context)
                .exceptionallyCompose(new Function<Throwable, CompletionStage<ToolExecutionResult>>() {
                    @Override
                    public CompletionStage<ToolExecutionResult> apply(Throwable error) {
                        Throwable cause = error instanceof CompletionException && error.getCause() != null
                                ? error.getCause()
                                : error;
                        if (!(cause instanceof AsyncNotSupportedException)) {
                            return CompletableFuture.failedFuture(cause);
                        }
                        return QuarkusToolExecutor.runBlocking(new Callable<ToolExecutionResult>() {
                            @Override
                            public ToolExecutionResult call() {
                                return delegate.executeWithContext(request, context);
                            }
                        });
                    }
                });
    }
}
