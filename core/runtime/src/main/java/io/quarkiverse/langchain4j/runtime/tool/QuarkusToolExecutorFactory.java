package io.quarkiverse.langchain4j.runtime.tool;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import jakarta.inject.Singleton;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
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

            @Override
            public String execute(ToolExecutionRequest toolExecutionRequest, Object memoryId) {
                AtomicReference<BiFunction<ToolExecutionRequest, Object, String>> funRef = new AtomicReference<>(
                        new BiFunction<>() {
                            @Override
                            public String apply(ToolExecutionRequest toolExecutionRequest, Object o) {
                                return originalTool.execute(toolExecutionRequest, memoryId);
                            }
                        });

                for (QuarkusToolExecutor.Wrapper wrapper : wrappers) {
                    var currentFun = funRef.get();
                    BiFunction<ToolExecutionRequest, Object, String> newFunction = new BiFunction<>() {
                        @Override
                        public String apply(ToolExecutionRequest toolExecutionRequest, Object memoryId) {
                            return wrapper.wrap(toolExecutionRequest, memoryId, currentFun);
                        }
                    };
                    funRef.set(newFunction);
                }

                return funRef.get().apply(toolExecutionRequest, memoryId);
            }
        };
    }
}
