package org.acme.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.mockito.Mockito;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
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
    ChatLanguageModel model;

    @Test
    void blockingSum() {
        var toolExecution = create("blockingSum");

        Mockito.when(model.generate(anyList(), anyList()))
                .thenReturn(
                        Response.from(AiMessage.from(toolExecution), new TokenUsage(1)),
                        Response.from(AiMessage.from("The result is 2"), new TokenUsage(1)));

        assertEquals("The result is 2", aiService.chat("Execute 1 + 1"));
    }

    @Test
    void nonBlockingSum() {
        var toolExecution = create("nonBlockingSum");

        Mockito.when(model.generate(anyList(), anyList()))
                .thenReturn(
                        Response.from(AiMessage.from(toolExecution), new TokenUsage(1)),
                        Response.from(AiMessage.from("The result is 2"), new TokenUsage(1)));

        assertEquals("The result is 2", aiService.chat("Execute 1 + 1"));
    }

    @Test
    void virtualThreadSum() {
        var toolExecution = create("virtualSum");

        Mockito.when(model.generate(anyList(), anyList()))
                .thenReturn(
                        Response.from(AiMessage.from(toolExecution), new TokenUsage(1)),
                        Response.from(AiMessage.from("The result is 2"), new TokenUsage(1)));

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
