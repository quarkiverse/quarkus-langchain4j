package io.quarkiverse.langchain4j.llama3;

import static io.quarkiverse.langchain4j.runtime.LangChain4jUtil.chatMessageToText;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import io.quarkiverse.langchain4j.llama3.copy.ChatFormat;

final class MessageMapper {

    static ChatFormat.Message toLlama3Message(ChatMessage langchainMessage) {
        ChatFormat.Role role = toJllamaRole(langchainMessage.type());
        return new ChatFormat.Message(role, chatMessageToText(langchainMessage));
    }

    private static ChatFormat.Role toJllamaRole(ChatMessageType chatMessageType) {
        return switch (chatMessageType) {
            case SYSTEM -> ChatFormat.Role.SYSTEM;
            case USER -> ChatFormat.Role.USER;
            case AI -> ChatFormat.Role.ASSISTANT;
            default -> throw new IllegalArgumentException("Unsupported chat message type: " + chatMessageType);
        };
    }
}
