package io.quarkiverse.langchain4j.runtime.template;

import java.util.List;
import java.util.StringJoiner;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import io.quarkus.qute.TemplateExtension;

@TemplateExtension
public class ChatMessageTemplateExtension {

    /**
     * Extracts and formats a dialogue between the user and the assistant from a list of chat messages. The user and assistant
     * messages are prefixed with the provided {@code userPrefix} and {@code assistantPrefix}, separated by the specified
     * {@code delimiter}.
     *
     * @param chatMessages the list of chat messages to process.
     * @param userPrefix the prefix for user messages.
     * @param assistantPrefix the prefix for assistant messages.
     * @param delimiter the delimiter between each message.
     * @return A formatted string representing the conversation between the user and the assistant.
     */
    static String extractDialogue(List<ChatMessage> chatMessages, String userPrefix, String assistantPrefix, String delimiter) {

        if (chatMessages == null || chatMessages.isEmpty())
            return "";

        StringJoiner joiner = new StringJoiner(delimiter == null ? "\n" : delimiter);
        userPrefix = (userPrefix == null) ? "User: " : userPrefix;
        assistantPrefix = (assistantPrefix == null) ? "Assistant: " : assistantPrefix;

        for (ChatMessage chatMessage : chatMessages) {
            switch (chatMessage.type()) {
                case AI -> {
                    AiMessage aiMessage = (AiMessage) chatMessage;
                    if (!aiMessage.hasToolExecutionRequests())
                        joiner.add("%s%s".formatted(assistantPrefix, aiMessage.text()));
                }
                case USER -> joiner.add("%s%s".formatted(userPrefix, chatMessage.text()));
                case SYSTEM, TOOL_EXECUTION_RESULT -> {
                    continue;
                }
                default -> {
                    continue;
                }
            }
        }

        return joiner.toString();
    }

    /**
     * Extracts and formats a dialogue between the user and the assistant from a list of chat messages.
     *
     * @param chatMessages the list of chat messages to process.
     * @param delimiter the delimiter between each message.
     * @return A formatted string representing the conversation between the user and the assistant.
     *
     */
    static String extractDialogue(List<ChatMessage> chatMessages, String delimiter) {
        return extractDialogue(chatMessages, null, null, delimiter);
    }

    /**
     * Extracts and formats a dialogue between the user and the assistant from a list of chat messages.
     *
     * @param chatMessages the list of chat messages to process.
     * @return A formatted string representing the conversation between the user and the assistant.
     *
     */
    static String extractDialogue(List<ChatMessage> chatMessages) {
        return extractDialogue(chatMessages, null, null, null);
    }
}
