package io.quarkiverse.langchain4j;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.service.tool.ToolArgumentsErrorHandler;
import dev.langchain4j.service.tool.ToolErrorContext;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;

/**
 * Can be used on a static method of an AI Service interface registered with {@link RegisterAiService}
 * to handle {@link ToolArgumentsException}.
 * <p>
 * The method can specify {@link Throwable} and/or {@link ToolErrorContext} as parameters.
 * The return type of the method must be either {@link String} or {@link ToolErrorHandlerResult}
 * <p>
 * See also: {@link ToolArgumentsErrorHandler}
 */
@Retention(RUNTIME)
@Target({ METHOD })
public @interface HandleToolArgumentError {
}
