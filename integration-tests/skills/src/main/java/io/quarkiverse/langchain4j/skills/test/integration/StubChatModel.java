package io.quarkiverse.langchain4j.skills.test.integration;

import java.util.List;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * If the {@code greeting} skill is advertised in the system message, activates it, then reads its resource,
 * and finally answers with the results of both tool calls, so that the test can verify that the skills were loaded
 * correctly.
 */
public class StubChatModel implements ChatModel {

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        List<ChatMessage> messages = chatRequest.messages();
        ChatMessage lastMessage = messages.get(messages.size() - 1);
        if (!(lastMessage instanceof ToolExecutionResultMessage)) {
            boolean skillAdvertised = messages.stream()
                    .anyMatch(m -> m instanceof SystemMessage systemMessage
                            && systemMessage.text().contains("<name>greeting</name>"));
            if (!skillAdvertised) {
                return ChatResponse.builder().aiMessage(AiMessage.from("The greeting skill is not available")).build();
            }
            return toolCall("activate_skill", "{\"skill_name\": \"greeting\"}");
        }
        if (((ToolExecutionResultMessage) lastMessage).toolName().equals("activate_skill")) {
            return toolCall("read_skill_resource",
                    "{\"skill_name\": \"greeting\", \"relative_path\": \"references/guide.md\"}");
        }

        StringBuilder answer = new StringBuilder();
        for (ChatMessage message : messages) {
            if (message instanceof ToolExecutionResultMessage toolResult) {
                answer.append(toolResult.toolName()).append(": ").append(toolResult.text()).append('\n');
            }
        }
        return ChatResponse.builder().aiMessage(AiMessage.from(answer.toString())).build();
    }

    private static ChatResponse toolCall(String name, String arguments) {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id(name)
                .name(name)
                .arguments(arguments)
                .build();
        return ChatResponse.builder().aiMessage(AiMessage.from(request)).build();
    }
}
