package io.quarkiverse.langchain4j.runtime;

import dev.langchain4j.service.tool.ToolExecutionErrorHandler;

/**
 * Marker interface that prevents the {@link ToolExecutionErrorHandler} from being engaged
 */
public interface PreventsErrorHandlerExecution {
}
