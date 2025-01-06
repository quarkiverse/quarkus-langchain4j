package io.quarkiverse.langchain4j.test.toolresolution;

import static dev.langchain4j.data.message.ChatMessageType.TOOL_EXECUTION_RESULT;

import java.util.List;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

public class TestAiModel implements ChatLanguageModel {
    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        return new Response<>(new AiMessage("NO TOOL"));
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        ChatMessage lastMsg = messages.get(messages.size() - 1);
        boolean isLastMsgToolResponse = lastMsg.type().equals(TOOL_EXECUTION_RESULT);
        if (isLastMsgToolResponse) {
            ToolExecutionResultMessage msg = (ToolExecutionResultMessage) lastMsg;
            return new Response<>(new AiMessage(msg.text()));
        }
        ToolSpecification toolSpecification = toolSpecifications.get(0);
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name(toolSpecification.name())
                .id(toolSpecification.name())
                .build();
        TokenUsage usage = new TokenUsage(42, 42);
        return new Response<>(AiMessage.from(toolExecutionRequest), usage, FinishReason.TOOL_EXECUTION);
    }
}
