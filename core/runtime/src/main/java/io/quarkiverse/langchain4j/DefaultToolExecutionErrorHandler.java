package io.quarkiverse.langchain4j;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Qualifier;

/**
 * Specifies the default error handler for tool execution for the entire project for all AI Services.
 * This handler will be a CDI bean with the default scope of {@link jakarta.inject.Singleton}.
 *
 * If more than one class is annotated with this qualifier, then you will get a deployment error.
 */
@Qualifier
@Retention(RUNTIME)
@Target(ElementType.TYPE)
public @interface DefaultToolExecutionErrorHandler {

    class Literal extends AnnotationLiteral<DefaultToolExecutionErrorHandler> implements DefaultToolExecutionErrorHandler {
        public static final Literal INSTANCE = new Literal();
    }
}
