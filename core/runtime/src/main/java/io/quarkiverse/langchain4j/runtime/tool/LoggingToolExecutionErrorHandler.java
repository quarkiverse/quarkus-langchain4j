package io.quarkiverse.langchain4j.runtime.tool;

import static dev.langchain4j.internal.Utils.isNullOrBlank;

import org.jboss.logging.Logger;

import dev.langchain4j.service.tool.ToolErrorContext;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;

public class LoggingToolExecutionErrorHandler implements ToolExecutionErrorHandler {
    private static final Logger log = Logger.getLogger("Tool Error");

    @Override
    public ToolErrorHandlerResult handle(Throwable error, ToolErrorContext context) {
        log.error(error);
        return ToolErrorHandlerResult.text(isNullOrBlank(error.getMessage()) ? error.getClass().getName() : error.getMessage());
    }
}
