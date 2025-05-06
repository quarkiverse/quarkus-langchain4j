package org.acme.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.mockito.Mockito;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@EnabledForJreRange(min = JRE.JAVA_21)
@SuppressWarnings("unchecked")
public class ToolsTest {

    @Inject
    AiService aiService;

    @InjectMock
    ChatModel model;

    @Test
    void blockingSum() {
        var toolExecution = create("blockingSum");

        Mockito.when(model.chat(Mockito.any(ChatRequest.class)))
                .thenReturn(
                        ChatResponse.builder().aiMessage(AiMessage.from(toolExecution)).tokenUsage(new TokenUsage(1)).build(),
                        ChatResponse.builder().aiMessage(AiMessage.from("The result is 2")).tokenUsage(new TokenUsage(1))
                                .build());

        assertEquals("The result is 2", aiService.chat("Execute 1 + 1"));
    }

    @Test
    void nonBlockingSum() {
        var toolExecution = create("nonBlockingSum");

        Mockito.when(model.chat(Mockito.any(ChatRequest.class)))
                .thenReturn(
                        ChatResponse.builder().aiMessage(AiMessage.from(toolExecution)).tokenUsage(new TokenUsage(1)).build(),
                        ChatResponse.builder().aiMessage(AiMessage.from("The result is 2")).tokenUsage(new TokenUsage(1))
                                .build());

        assertEquals("The result is 2", aiService.chat("Execute 1 + 1"));
    }

    @Test
    void virtualThreadSum() {
        var toolExecution = create("virtualSum");

        Mockito.when(model.chat(Mockito.any(ChatRequest.class)))
                .thenReturn(
                        ChatResponse.builder().aiMessage(AiMessage.from(toolExecution)).tokenUsage(new TokenUsage(1)).build(),
                        ChatResponse.builder().aiMessage(AiMessage.from("The result is 2")).tokenUsage(new TokenUsage(1))
                                .build());

        assertEquals("The result is 2", aiService.chat("Execute 1 + 1"));
    }

    private ToolExecutionRequest create(String methodName) {
        return ToolExecutionRequest.builder()
                .id("1")
                .name(methodName)
                .arguments("""
                            {
                              "a": 1,
                              "b": 1
                            }
                        """)
                .build();
    }
}
