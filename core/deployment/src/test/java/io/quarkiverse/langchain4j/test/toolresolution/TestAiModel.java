package io.quarkiverse.langchain4j.test.toolresolution;

import static dev.langchain4j.data.message.ChatMessageType.TOOL_EXECUTION_RESULT;

import java.util.List;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;

public class TestAiModel implements ChatLanguageModel {

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        List<ChatMessage> messages = chatRequest.messages();
        ChatMessage lastMsg = messages.get(messages.size() - 1);
        boolean isLastMsgToolResponse = lastMsg.type().equals(TOOL_EXECUTION_RESULT);
        if (isLastMsgToolResponse) {
            ToolExecutionResultMessage msg = (ToolExecutionResultMessage) lastMsg;
            return ChatResponse.builder().aiMessage(new AiMessage(msg.text())).build();
        }
        if (chatRequest.toolSpecifications() == null || chatRequest.toolSpecifications().isEmpty()) {
            return ChatResponse.builder().aiMessage(new AiMessage("NO TOOL")).build();
        }
        ToolSpecification toolSpecification = chatRequest.toolSpecifications().get(0);
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name(toolSpecification.name())
                .id(toolSpecification.name())
                .build();
        TokenUsage usage = new TokenUsage(42, 42);
        return ChatResponse.builder().aiMessage(AiMessage.from(toolExecutionRequest))
                .tokenUsage(usage)
                .finishReason(FinishReason.TOOL_EXECUTION)
                .build();
    }
}
