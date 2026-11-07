package io.quarkiverse.langchain4j.agentic.runtime;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark a parameter of {@link dev.langchain4j.agentic.declarative.ChatModelSupplier} and
 * {@link dev.langchain4j.agentic.declarative.StreamingChatModelSupplier} as a CDI bean.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface CdiBean {
}
