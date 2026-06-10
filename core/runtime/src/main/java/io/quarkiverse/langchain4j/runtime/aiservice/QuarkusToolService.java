package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.function.Consumer;

import dev.langchain4j.service.tool.BeforeToolExecution;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolService;

/**
 * Extends {@link ToolService} to expose the stored before/after tool execution callbacks.
 * <p>
 * Upstream langchain4j stores these callbacks as private fields with setters but no getters.
 * Quarkus needs access to them so it can fire the callbacks manually while preserving
 * {@link io.quarkiverse.langchain4j.runtime.PreventsErrorHandlerExecution} semantics
 * in its own tool execution error handling.
 */
public class QuarkusToolService extends ToolService {

    private Consumer<BeforeToolExecution> storedBeforeToolExecution;
    private Consumer<ToolExecution> storedAfterToolExecution;

    @Override
    public void beforeToolExecution(Consumer<BeforeToolExecution> beforeToolExecution) {
        this.storedBeforeToolExecution = beforeToolExecution;
        super.beforeToolExecution(beforeToolExecution);
    }

    @Override
    public void afterToolExecution(Consumer<ToolExecution> afterToolExecution) {
        this.storedAfterToolExecution = afterToolExecution;
        super.afterToolExecution(afterToolExecution);
    }

    public Consumer<BeforeToolExecution> getBeforeToolExecution() {
        return storedBeforeToolExecution;
    }

    public Consumer<ToolExecution> getAfterToolExecution() {
        return storedAfterToolExecution;
    }
}
