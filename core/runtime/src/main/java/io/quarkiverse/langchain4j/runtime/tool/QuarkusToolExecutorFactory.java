package io.quarkiverse.langchain4j.runtime.tool;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import jakarta.inject.Singleton;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.quarkus.arc.All;
import io.quarkus.arc.Unremovable;

@Singleton
@Unremovable
public class QuarkusToolExecutorFactory {

    private final List<QuarkusToolExecutor.Wrapper> wrappers;

    public QuarkusToolExecutorFactory(@All List<QuarkusToolExecutor.Wrapper> wrappers) {
        this.wrappers = wrappers;
    }

    public QuarkusToolExecutor create(QuarkusToolExecutor.Context context) {
        if (wrappers.isEmpty()) {
            return new QuarkusToolExecutor(context);
        }

        return new QuarkusToolExecutor(context) {
            final QuarkusToolExecutor originalTool = new QuarkusToolExecutor(context);
            final QuarkusToolExecutor executor = this;

            @Override
            public ToolExecutionResult executeWithContext(ToolExecutionRequest toolExecutionRequest,
                    InvocationContext invocationContext) {

                AtomicReference<BiFunction<ToolExecutionRequest, InvocationContext, ToolExecutionResult>> funRef = new AtomicReference<>(
                        originalTool::executeWithContext);

                for (QuarkusToolExecutor.Wrapper wrapper : wrappers) {
                    var currentFun = funRef.get();
                    BiFunction<ToolExecutionRequest, InvocationContext, ToolExecutionResult> newFunction = (
                            toolExecutionRequest1,
                            invocationContext1) -> wrapper.wrap(toolExecutionRequest1, invocationContext1, currentFun,
                                    executor);
                    funRef.set(newFunction);
                }

                return funRef.get().apply(toolExecutionRequest, invocationContext);
            }

            @Override
            public CompletableFuture<ToolExecutionResult> executeAsync(ToolExecutionRequest toolExecutionRequest,
                    InvocationContext invocationContext) {
                BiFunction<ToolExecutionRequest, InvocationContext, CompletableFuture<ToolExecutionResult>> fun = new BiFunction<>() {
                    @Override
                    public CompletableFuture<ToolExecutionResult> apply(ToolExecutionRequest request,
                            InvocationContext context) {
                        return originalTool.executeAsync(request, context);
                    }
                };
                for (QuarkusToolExecutor.Wrapper wrapper : wrappers) {
                    BiFunction<ToolExecutionRequest, InvocationContext, CompletableFuture<ToolExecutionResult>> next = fun;
                    fun = new BiFunction<>() {
                        @Override
                        public CompletableFuture<ToolExecutionResult> apply(ToolExecutionRequest request,
                                InvocationContext context) {
                            return wrapper.wrapAsync(request, context, next, executor);
                        }
                    };
                }
                return fun.apply(toolExecutionRequest, invocationContext);
            }
        };
    }
}
