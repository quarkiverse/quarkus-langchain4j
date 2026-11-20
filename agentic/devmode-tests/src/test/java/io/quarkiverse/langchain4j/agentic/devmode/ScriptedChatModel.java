package io.quarkiverse.langchain4j.agentic.devmode;

import java.util.List;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

/**
 * Offline {@link ChatModel} answering by agent role, calling the word counter tool once for the writer.
 */
public class ScriptedChatModel implements ChatModel {

    private final String role;

    public ScriptedChatModel(String role) {
        this.role = role;
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        String user = lastUserText(request.messages());
        String text;
        switch (role) {
            case "writer" -> {
                if (!hasToolResult(request.messages())) {
                    ToolExecutionRequest toolRequest = ToolExecutionRequest.builder().id("call_1")
                            .name("countWords").arguments("{\"text\":\"" + user + "\"}").build();
                    return ChatResponse.builder().aiMessage(AiMessage.from(toolRequest))
                            .tokenUsage(new TokenUsage(80, 10)).build();
                }
                text = "Once upon a time " + user + " met under a silver moon and changed the kingdom forever";
            }
            case "scorer" -> text = user.contains("Edited") ? "0.9" : "0.4";
            case "editor" -> {
                if (user.contains("fail")) {
                    throw new IllegalStateException("Style editor rejected the draft");
                }
                text = "Edited: " + user;
            }
            default -> text = role + ": " + user;
        }
        return ChatResponse.builder().aiMessage(new AiMessage(text)).tokenUsage(new TokenUsage(100, 20)).build();
    }

    private static boolean hasToolResult(List<ChatMessage> messages) {
        for (ChatMessage message : messages) {
            if (message instanceof ToolExecutionResultMessage) {
                return true;
            }
        }
        return false;
    }

    private static String lastUserText(List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage userMessage) {
                return userMessage.singleText();
            }
        }
        return "";
    }
}
