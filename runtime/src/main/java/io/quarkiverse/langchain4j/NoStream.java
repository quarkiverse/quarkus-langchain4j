package io.quarkiverse.langchain4j;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.interceptor.InterceptorBinding;

import dev.ai4j.openai4j.chat.ChatCompletionRequest;

/**
 * Turns a request into a streaming request
 * <p>
 * Supported types are: {@link ChatCompletionRequest}
 */
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
public @interface NoStream {
}
