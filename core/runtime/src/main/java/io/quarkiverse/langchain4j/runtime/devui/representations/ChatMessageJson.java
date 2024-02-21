package io.quarkiverse.langchain4j.runtime.devui.representations;

import java.util.List;
import java.util.stream.Collectors;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;

// The representation of a chat message that it sent to the Dev UI as JSON
public class ChatMessageJson {

    private MessageType type;
    private String message;

    public MessageType getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public static List<ChatMessageJson> listFromMemory(ChatMemory memory) {
        return memory.messages()
                .stream()
                .map(ChatMessageJson::fromMessage)
                .collect(Collectors.toList());
    }

    public static ChatMessageJson fromMessage(ChatMessage message) {
        ChatMessageJson json = new ChatMessageJson();
        switch (message.type()) {
            case SYSTEM:
                json.type = MessageType.SYSTEM;
                json.message = ((SystemMessage) message).text();
                break;
            case USER:
                json.type = MessageType.USER;
                json.message = ((UserMessage) message).text();
                break;
            case AI:
                json.type = MessageType.AI;
                json.message = ((AiMessage) message).text();
                break;
        }
        return json;
    }

}
