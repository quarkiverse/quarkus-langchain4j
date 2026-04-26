package io.quarkiverse.langchain4j;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.service.tool.ToolErrorContext;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;

/**
 * Can be used on a static method of an AI Service interface registered with {@link RegisterAiService}
 * to handle {@link ToolExecutionException}.
 * <p>
 * The method can specify {@link Throwable} and/or {@link ToolErrorContext} as parameters.
 * The return type of the method must be either {@link String} or {@link ToolErrorHandlerResult}
 * <p>
 * See also: {@link ToolExecutionErrorHandler}
 */
@Retention(RUNTIME)
@Target({ METHOD })
public @interface HandleToolExecutionError {
}
