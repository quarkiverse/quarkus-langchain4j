package io.quarkiverse.langchain4j.guardrails;

import java.util.List;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.guardrail.ChatExecutor;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * This is needed for output guardrails on a method returning Multi.
 * The LC4J api requires a {@link ChatExecutor} passed into the {@link dev.langchain4j.guardrail.OutputGuardrailExecutor},
 * but in the case of a Multi, we do not want the {@link dev.langchain4j.guardrail.OutputGuardrailExecutor} to re-execute the
 * request.
 * Instead, we will fail and retry the Multi itself
 */
public final class NoopChatExecutor implements ChatExecutor {
    @Override
    public ChatResponse execute() {
        return execute(List.of());
    }

    @Override
    public ChatResponse execute(List<ChatMessage> chatMessages) {
        return null;
    }
}
