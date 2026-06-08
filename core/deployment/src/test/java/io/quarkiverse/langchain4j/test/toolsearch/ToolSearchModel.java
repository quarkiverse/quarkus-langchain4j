package io.quarkiverse.langchain4j.test.toolsearch;

import static dev.langchain4j.data.message.ChatMessageType.TOOL_EXECUTION_RESULT;

import java.util.List;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;

/**
 * Drives the tool-search flow deterministically and encodes the expectations of each stage so the test fails with a
 * descriptive answer if any stage misbehaves:
 * <ul>
 * <li>first turn: the searchable tool must be hidden and only the search tool offered;</li>
 * <li>after the search tool runs: the found tool must have been added to the effective tools;</li>
 * <li>finally: the found tool result is returned as the answer.</li>
 * </ul>
 */
public class ToolSearchModel implements ChatModel {

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        List<ChatMessage> messages = chatRequest.messages();
        ChatMessage lastMessage = messages.get(messages.size() - 1);

        if (lastMessage.type().equals(TOOL_EXECUTION_RESULT)) {
            ToolExecutionResultMessage result = (ToolExecutionResultMessage) lastMessage;
            // Tool results are JSON-serialized, so the real tool result text is quoted.
            if (result.text().contains("REAL_TOOL_RESULT")) {
                return answer("REAL_TOOL_RESULT");
            }
            // The search tool just ran; the found tool must now be available.
            if (hasTool(chatRequest, FakeToolSearchStrategy.FOUND_TOOL_NAME)) {
                return callTool(FakeToolSearchStrategy.FOUND_TOOL_NAME);
            }
            return answer("FOUND_TOOL_NOT_AVAILABLE");
        }

        // First turn: the searchable tool must be hidden, only the search tool exposed.
        if (hasTool(chatRequest, FakeToolSearchStrategy.FOUND_TOOL_NAME)) {
            return answer("TOOL_NOT_NARROWED");
        }
        if (hasTool(chatRequest, FakeToolSearchStrategy.SEARCH_TOOL_NAME)) {
            return callTool(FakeToolSearchStrategy.SEARCH_TOOL_NAME);
        }
        return answer("NO_SEARCH_TOOL");
    }

    private static boolean hasTool(ChatRequest chatRequest, String name) {
        List<ToolSpecification> toolSpecifications = chatRequest.toolSpecifications();
        if (toolSpecifications == null) {
            return false;
        }
        return toolSpecifications.stream().anyMatch(spec -> spec.name().equals(name));
    }

    private static ChatResponse callTool(String name) {
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name(name)
                .id(name)
                .build();
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(toolExecutionRequest))
                .finishReason(FinishReason.TOOL_EXECUTION)
                .build();
    }

    private static ChatResponse answer(String text) {
        return ChatResponse.builder().aiMessage(new AiMessage(text)).build();
    }
}
