package io.quarkiverse.langchain4j.agentic.runtime;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a parameter of an agentic supplier method as a CDI bean, including
 * {@link dev.langchain4j.agentic.declarative.ChatModelSupplier},
 * {@link dev.langchain4j.agentic.declarative.StreamingChatModelSupplier}, and
 * {@link dev.langchain4j.agentic.declarative.ParallelExecutor}.
 * CDI qualifiers on the parameter select the bean to resolve.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface CdiBean {
}
