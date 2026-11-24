package io.quarkiverse.langchain4j.runtime.listeners;

import jakarta.inject.Singleton;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.opentelemetry.api.trace.Span;

/**
 * Adds the completion to the model span if so configured by the user.
 * <p>
 * The completion is recorded as the GenAI semantic conventions {@code gen_ai.output.messages} attribute. The deprecated
 * {@code gen_ai.completion} attribute is still recorded so that existing consumers keep working. It will be removed in a
 * future release.
 */
@Singleton
public class CompletionTextSpanContributor implements ChatModelSpanContributor {

    private final boolean includeCompletion;

    public CompletionTextSpanContributor(
            @ConfigProperty(name = "quarkus.langchain4j.tracing.include-completion") boolean includeCompletion) {
        this.includeCompletion = includeCompletion;
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext, Span currentSpan) {
        if (!includeCompletion) {
            return;
        }
        ChatResponse response = responseContext.chatResponse();
        currentSpan.setAttribute(GenAiMessageAttributes.GEN_AI_OUTPUT_MESSAGES,
                GenAiMessageAttributes.outputMessages(response));
        currentSpan.setAttribute(GenAiMessageAttributes.GEN_AI_COMPLETION, response.aiMessage().text());
    }
}
