package io.quarkiverse.langchain4j.runtime.devui.json;

import java.util.List;
import java.util.stream.Collectors;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;

// The representation of a chat message that it sent to the Dev UI as JSON
public class ChatMessagePojo {

    private MessageType type;
    private String message;
    private List<ToolExecutionRequestPojo> toolExecutionRequests;
    private ToolExecutionResultPojo toolExecutionResult;

    public MessageType getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public List<ToolExecutionRequestPojo> getToolExecutionRequests() {
        return toolExecutionRequests;
    }

    public ToolExecutionResultPojo getToolExecutionResult() {
        return toolExecutionResult;
    }

    public static List<ChatMessagePojo> listFromMemory(ChatMemory memory) {
        return memory.messages()
                .stream()
                .map(ChatMessagePojo::fromMessage)
                .collect(Collectors.toList());
    }

    public static ChatMessagePojo fromMessage(ChatMessage message) {
        ChatMessagePojo json = new ChatMessagePojo();
        switch (message.type()) {
            case SYSTEM:
                json.type = MessageType.SYSTEM;
                json.message = ((SystemMessage) message).text();
                break;
            case USER:
                json.type = MessageType.USER;
                UserMessage userMessage = (UserMessage) message;
                json.message = userMessage.hasSingleText() ? userMessage.singleText() : null;
                break;
            case AI:
                AiMessage aiMessage = (AiMessage) message;
                json.type = MessageType.AI;
                json.message = ((AiMessage) message).text();
                if (aiMessage.toolExecutionRequests() != null && !aiMessage.toolExecutionRequests().isEmpty()) {
                    json.toolExecutionRequests = ((AiMessage) message)
                            .toolExecutionRequests().stream()
                            .map(r -> new ToolExecutionRequestPojo(r.id(), r.name(), r.arguments()))
                            .collect(Collectors.toList());
                }
                break;
            case TOOL_EXECUTION_RESULT:
                json.type = MessageType.TOOL_EXECUTION_RESULT;
                json.message = null;
                json.toolExecutionResult = new ToolExecutionResultPojo(
                        ((ToolExecutionResultMessage) message).id(),
                        ((ToolExecutionResultMessage) message).toolName(),
                        ((ToolExecutionResultMessage) message).text());
                break;
        }
        return json;
    }

}
