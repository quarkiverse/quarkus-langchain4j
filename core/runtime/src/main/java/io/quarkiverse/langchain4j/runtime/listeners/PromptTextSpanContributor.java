package io.quarkiverse.langchain4j.runtime.listeners;

import java.util.List;
import java.util.Optional;

import jakarta.inject.Singleton;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import io.opentelemetry.api.common.Value;
import io.opentelemetry.api.trace.Span;
import io.quarkiverse.langchain4j.runtime.LangChain4jUtil;

/**
 * Adds the prompt to the model span if so configured by the user.
 * <p>
 * The prompt is recorded as the GenAI semantic conventions {@code gen_ai.input.messages} attribute. When the model
 * provider takes the system instructions separately from the chat history, the system messages are recorded as
 * {@code gen_ai.system_instructions} instead. The deprecated {@code gen_ai.prompt} attribute is still recorded so that
 * existing consumers keep working. It will be removed in a future release.
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

        List<ChatMessage> messages = requestContext.chatRequest().messages();
        boolean isSystemSeparate = GenAiMessageAttributes.sendsSystemInstructionsSeparately(requestContext.modelProvider());
        if (isSystemSeparate) {
            Optional<Value<?>> systemInstructions = GenAiMessageAttributes.systemInstructions(messages);
            if (systemInstructions.isPresent()) {
                currentSpan.setAttribute(GenAiMessageAttributes.GEN_AI_SYSTEM_INSTRUCTIONS, systemInstructions.get());
            }
        }

        currentSpan.setAttribute(GenAiMessageAttributes.GEN_AI_INPUT_MESSAGES,
                GenAiMessageAttributes.inputMessages(messages, !isSystemSeparate));
        currentSpan.setAttribute(GenAiMessageAttributes.GEN_AI_PROMPT, legacyPrompt(messages));
    }

    private static String legacyPrompt(List<ChatMessage> messages) {
        var prompt = new StringBuilder();
        for (ChatMessage message : messages) {
            if (!prompt.isEmpty()) {
                prompt.append('\n');
            }
            prompt.append(legacyText(message));
        }
        return prompt.toString();
    }

    private static String legacyText(ChatMessage message) {
        if ((message instanceof ToolExecutionResultMessage toolResult) && !toolResult.hasSingleText()) {
            var text = new StringBuilder();
            for (Content content : toolResult.contents()) {
                if (content instanceof TextContent textContent) {
                    if (!text.isEmpty()) {
                        text.append('\n');
                    }
                    text.append(textContent.text());
                }
            }
            return text.toString();
        }
        return LangChain4jUtil.chatMessageToText(message);
    }
}
