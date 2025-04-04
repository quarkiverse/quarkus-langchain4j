package io.quarkiverse.langchain4j.runtime.listeners;

import java.util.stream.Collectors;

import jakarta.inject.Singleton;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import io.opentelemetry.api.trace.Span;
import io.quarkiverse.langchain4j.runtime.LangChain4jUtil;

/**
 * Adds the prompt as a span attribute if so configured by the user
 */
@Singleton
public class PromptTextSpanContributor implements ChatModelSpanContributor {

    private final boolean includePrompt;

    public PromptTextSpanContributor(
            @ConfigProperty(name = "quarkus.langchain4j.tracing.include-prompt") boolean includePrompt) {
        this.includePrompt = includePrompt;
    }

    @Override
    public void onRequest(ChatModelRequestContext requestContext, Span currentSpan) {
        if (!includePrompt) {
            return;
        }
        currentSpan.setAttribute("gen_ai.prompt", requestContext.chatRequest().messages().stream().map(
                LangChain4jUtil::chatMessageToText).collect(
                        Collectors.joining("\n")));
    }
}
