package io.quarkiverse.langchain4j.runtime.listeners;

import jakarta.inject.Singleton;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import io.opentelemetry.api.trace.Span;

/**
 * Adds the prompt as a span attribute if so configured by the user
 */
@Singleton
public class CompletionTextSpanContributor implements ChatModelSpanContributor {

    private final boolean includeCompletion;

    public CompletionTextSpanContributor(
            @ConfigProperty(name = "quarkus.langchain4j.tracing.include-completion") boolean includeCompletion) {
        this.includeCompletion = includeCompletion;
    }

    @Override
    public void onResponse(ChatModelResponseContext requestContext, Span currentSpan) {
        if (!includeCompletion) {
            return;
        }
        currentSpan.setAttribute("gen_ai.completion", requestContext.chatResponse().aiMessage().text());
    }
}
