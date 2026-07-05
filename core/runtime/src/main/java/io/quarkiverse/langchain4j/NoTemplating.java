package io.quarkiverse.langchain4j;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Disables Qute templating for a {@code @UserMessage} method parameter, so its value is used verbatim.
 * <p>
 * Use it when the parameter value may contain {@code {...}} sequences that must not be interpreted as
 * template expressions.
 */
@Target(ElementType.PARAMETER)
@Retention(RUNTIME)
@Documented
public @interface NoTemplating {
}
