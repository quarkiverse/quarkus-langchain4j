package org.acme.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;

/**
 * End-to-end check that the {@code toolSearchStrategySupplier} option works in a running application: the catalog is
 * narrowed to the search tool, and the tool surfaced by the search is added and executed.
 */
@QuarkusTest
public class ToolSearchToolsTest {

    @Inject
    ToolSearchAiService aiService;

    @InjectMock
    ChatModel model;

    @Test
    void searchedToolIsExecuted() {
        Mockito.when(model.chat(Mockito.any(ChatRequest.class))).thenAnswer(invocation -> {
            ChatRequest request = invocation.getArgument(0);
            List<ChatMessage> messages = request.messages();
            ChatMessage last = messages.get(messages.size() - 1);
            Set<String> toolNames = request.toolSpecifications() == null ? Set.of()
                    : request.toolSpecifications().stream().map(ToolSpecification::name).collect(Collectors.toSet());

            if (last instanceof ToolExecutionResultMessage result) {
                if (result.text().contains("REAL_TOOL_RESULT")) {
                    return response(AiMessage.from("done"));
                }
                // The search tool ran, so the surfaced tool must now be available.
                assertTrue(toolNames.contains(FixedToolSearchStrategy.FOUND_TOOL_NAME));
                return response(AiMessage.from(toolCall(FixedToolSearchStrategy.FOUND_TOOL_NAME)));
            }

            // First request: the catalog must be narrowed to the search tool only.
            assertFalse(toolNames.contains(FixedToolSearchStrategy.FOUND_TOOL_NAME));
            assertTrue(toolNames.contains(FixedToolSearchStrategy.SEARCH_TOOL_NAME));
            return response(AiMessage.from(toolCall(FixedToolSearchStrategy.SEARCH_TOOL_NAME)));
        });

        assertEquals("done", aiService.chat("get my booking details", 1));
    }

    private static ToolExecutionRequest toolCall(String name) {
        return ToolExecutionRequest.builder().id(name).name(name).arguments("{}").build();
    }

    private static ChatResponse response(AiMessage aiMessage) {
        return ChatResponse.builder()
                .aiMessage(aiMessage)
                .tokenUsage(new TokenUsage(1))
                .finishReason(aiMessage.hasToolExecutionRequests() ? FinishReason.TOOL_EXECUTION : FinishReason.STOP)
                .build();
    }
}
