package io.quarkiverse.langchain4j.agentic.runtime;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.interceptor.InterceptorBinding;

/**
 * The interceptor binding added at build time to the class-based agents whose scope is not the application scope,
 * so that {@link AgentLifecycleInterceptor} evicts the agentic scope and the chat memories of a bean instance when
 * the instance is destroyed. Not meant to be used in applications.
 */
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AgentLifecycle {
}
