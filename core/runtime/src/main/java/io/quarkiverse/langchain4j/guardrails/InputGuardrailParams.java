package io.quarkiverse.langchain4j.guardrails;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.rag.AugmentationResult;

/**
 * Represents the parameter passed to {@link InputGuardrail#validate(InputGuardrailParams)}.
 *
 * @param userMessage the user message, cannot be {@code null}
 * @param memory the memory, can be {@code null} or empty
 * @param augmentationResult the augmentation result, can be {@code null}
 * @param userMessageTemplate the user message template, cannot be {@code null}
 * @param variables the variable to be used with userMessageTemplate, cannot be {@code null}
 * @deprecated Use {@link dev.langchain4j.guardrail.InputGuardrailRequest} instead
 */
@Deprecated(forRemoval = true)
public record InputGuardrailParams(UserMessage userMessage, ChatMemory memory,
        AugmentationResult augmentationResult, String userMessageTemplate,
        Map<String, Object> variables) implements GuardrailParams {

    public static InputGuardrailParams from(UserMessage userMessage) {
        return from(userMessage, null);
    }

    public static InputGuardrailParams from(UserMessage userMessage, Map<String, Object> variables) {
        return new InputGuardrailParams(userMessage, null, null, null, Optional.ofNullable(variables).orElseGet(Map::of));
    }

    @Override
    public InputGuardrailParams withText(String text) {
        return new InputGuardrailParams(rewriteUserMessage(userMessage, text), memory, augmentationResult, userMessageTemplate,
                variables);
    }

    public static UserMessage rewriteUserMessage(UserMessage userMessage, String text) {
        List<Content> rewrittenContent = userMessage.contents().stream()
                .map(c -> c.type() == ContentType.TEXT ? new TextContent(text) : c).toList();
        return userMessage.name() == null ? new UserMessage(rewrittenContent)
                : new UserMessage(userMessage.name(), rewrittenContent);
    }
}
