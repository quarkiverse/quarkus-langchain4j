package org.acme.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.util.stream.Stream;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

    @ParameterizedTest(name = "blocking {1}")
    @MethodSource("getJavaTimeChats")
    void blockingJavaTime(String value, String methodName) {
        var toolExecution = createCalendar(methodName, value);

        Mockito.when(model.chat(Mockito.any(ChatRequest.class)))
                .thenReturn(
                        ChatResponse.builder().aiMessage(AiMessage.from(toolExecution)).tokenUsage(new TokenUsage(1)).build(),
                        ChatResponse.builder().aiMessage(AiMessage.from("Got %s".formatted(value)))
                                .tokenUsage(new TokenUsage(1))
                                .build());

        assertEquals("Got %s".formatted(value), aiService.calendarChat("Execute %s".formatted(methodName)));
    }

    protected static Stream<Arguments> getJavaTimeChats() {
        return Stream.of(
                Arguments.of(Instant.now().toString(), "instant"),
                Arguments.of(LocalDate.now().toString(), "date"),
                Arguments.of(LocalDateTime.now().toString(), "dateTime"),
                Arguments.of(LocalTime.now().toString(), "time"),
                Arguments.of(OffsetDateTime.now().toString(), "offsetDateTime"),
                Arguments.of(OffsetTime.now().toString(), "offsetTime"),
                Arguments.of(Year.now().toString(), "year"),
                Arguments.of(YearMonth.now().toString(), "yearMonth"),
                Arguments.of(Period.ofDays(3).toString(), "period"));
    }

    private ToolExecutionRequest createCalendar(String methodName, String value) {
        return ToolExecutionRequest.builder()
                .id("1")
                .name(methodName)
                .arguments(
                        """
                                {
                                  "variable": "%s"
                                }
                                """.formatted(value))
                .build();
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
