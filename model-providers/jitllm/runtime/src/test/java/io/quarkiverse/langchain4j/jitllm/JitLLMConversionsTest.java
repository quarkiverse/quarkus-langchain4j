package io.quarkiverse.langchain4j.jitllm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.beehive.jitllm.api.ChatContent;
import org.beehive.jitllm.api.ChatRole;
import org.beehive.jitllm.api.FinishReason;
import org.beehive.jitllm.api.GenerationResult;
import org.beehive.jitllm.api.GenerationTimings;
import org.beehive.jitllm.api.ToolSpec;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

/**
 * The extension's mapping, proven without a model.
 *
 * <p>
 * What a small model chooses to emit is its own business; whether a tool call is transported and
 * mapped correctly is this extension's. Every case is a pure function of its arguments: no engine,
 * no device, no model file, no Quarkus container.
 */
class JitLLMConversionsTest {

    @Test
    void oneCallWithArgumentsIsMappedWithItsIdNameAndArguments() {
        List<ToolExecutionRequest> requests = JitLLMConversions.toToolExecutionRequests(
                List.of(new ChatContent.ToolCall("call-1", "getWeather", "{\"city\":\"Munich\"}")));

        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).id()).isEqualTo("call-1");
        assertThat(requests.get(0).name()).isEqualTo("getWeather");
        assertThat(requests.get(0).arguments()).contains("Munich");
    }

    @Test
    void aNoArgumentCallKeepsAnEmptyJsonObject() {
        assertThat(JitLLMConversions.toToolExecutionRequests(
                List.of(new ChatContent.ToolCall("call-1", "get_current_time", "{}")))
                .get(0).arguments()).isEqualTo("{}");
    }

    @Test
    void twoCallsKeepTheirOrderAndTheirDistinctIds() {
        List<ToolExecutionRequest> requests = JitLLMConversions.toToolExecutionRequests(List.of(
                new ChatContent.ToolCall("call-1", "getTime", "{\"country\":\"France\"}"),
                new ChatContent.ToolCall("call-2", "getTemperature", "{\"city\":\"Munich\"}")));

        assertThat(requests).extracting(ToolExecutionRequest::id).containsExactly("call-1", "call-2");
        assertThat(requests).extracting(ToolExecutionRequest::name)
                .containsExactly("getTime", "getTemperature");
    }

    @Test
    void userAndSystemTurnsMapToTheirRoles() {
        assertThat(JitLLMConversions.toEngineMessages(
                List.of(SystemMessage.from("be brief"), UserMessage.from("hello"))))
                .extracting(org.beehive.jitllm.api.ChatMessage::role)
                .containsExactly(ChatRole.SYSTEM, ChatRole.USER);
    }

    @Test
    void anAssistantTurnWithToolCallsCarriesThemAsToolCallContent() {
        AiMessage ai = AiMessage.builder()
                .toolExecutionRequests(List.of(ToolExecutionRequest.builder()
                        .id("call-1").name("getWeather").arguments("{\"city\":\"Munich\"}").build()))
                .build();

        assertThat(JitLLMConversions.toAssistantMessage(ai).content()).singleElement()
                .isInstanceOfSatisfying(ChatContent.ToolCall.class,
                        call -> assertThat(call.id()).isEqualTo("call-1"));
    }

    @Test
    void aToolResultMapsBackWithItsIdAndName() {
        assertThat(JitLLMConversions.toEngineMessages(
                List.of(ToolExecutionResultMessage.from("call-1", "getWeather", "sunny"))))
                .singleElement().satisfies(message -> {
                    assertThat(message.role()).isEqualTo(ChatRole.TOOL);
                    assertThat(message.content()).singleElement()
                            .isInstanceOfSatisfying(ChatContent.ToolResult.class, result -> {
                                assertThat(result.id()).isEqualTo("call-1");
                                assertThat(result.name()).isEqualTo("getWeather");
                            });
                });
    }

    @Test
    void aFullRoundTripKeepsCallAndResultIdsMatched() {
        ToolExecutionRequest asked = JitLLMConversions.toToolExecutionRequests(
                List.of(new ChatContent.ToolCall("call-1", "getWeather", "{}"))).get(0);

        List<ChatMessage> conversation = List.of(
                UserMessage.from("weather?"),
                AiMessage.builder().toolExecutionRequests(List.of(asked)).build(),
                ToolExecutionResultMessage.from(asked.id(), asked.name(), "sunny"));

        List<org.beehive.jitllm.api.ChatMessage> converted = JitLLMConversions.toEngineMessages(conversation);

        String calledId = ((ChatContent.ToolCall) converted.get(1).content().get(0)).id();
        String answeredId = ((ChatContent.ToolResult) converted.get(2).content().get(0)).id();
        assertThat(answeredId).isEqualTo(calledId);
    }

    @Test
    void aMissingIdIsGeneratedRatherThanRejected() {
        AiMessage ai = AiMessage.builder()
                .toolExecutionRequests(List.of(
                        ToolExecutionRequest.builder().name("getWeather").arguments("{}").build()))
                .build();

        ChatContent.ToolCall call = (ChatContent.ToolCall) JitLLMConversions.toAssistantMessage(ai).content().get(0);
        assertThat(call.id()).startsWith("call_");
    }

    @Test
    void aToolCallReasonBecomesToolExecution() {
        assertThat(JitLLMConversions.toLangChain4jFinishReason(
                org.beehive.jitllm.api.FinishReason.TOOL_CALL))
                .isEqualTo(dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION);
    }

    @Test
    void everyOtherReasonMapsWithoutInventingAToolExecution() {
        assertThat(JitLLMConversions.toLangChain4jFinishReason(
                org.beehive.jitllm.api.FinishReason.MAX_TOKENS))
                .isEqualTo(dev.langchain4j.model.output.FinishReason.LENGTH);
        assertThat(JitLLMConversions.toLangChain4jFinishReason(
                org.beehive.jitllm.api.FinishReason.STOP_TOKEN))
                .isEqualTo(dev.langchain4j.model.output.FinishReason.STOP);
        assertThat(JitLLMConversions.toLangChain4jFinishReason(
                org.beehive.jitllm.api.FinishReason.CANCELLED))
                .isEqualTo(dev.langchain4j.model.output.FinishReason.OTHER);
    }

    @Test
    void everyEngineStopReasonIsMapped() {
        for (org.beehive.jitllm.api.FinishReason reason : org.beehive.jitllm.api.FinishReason.values()) {
            assertThat(JitLLMConversions.toLangChain4jFinishReason(reason)).isNotNull();
        }
    }

    @Test
    void synchronousAndStreamingDeriveTheSameRequestsFromTheSameCalls() {
        List<ChatContent.ToolCall> calls = List.of(
                new ChatContent.ToolCall("call-1", "getTime", "{}"),
                new ChatContent.ToolCall("call-2", "getTemperature", "{}"));

        assertThat(JitLLMConversions.toToolExecutionRequests(calls))
                .isEqualTo(JitLLMConversions.toToolExecutionRequests(calls));
    }

    @Test
    void aJsonStringLiteralToolResultIsUnwrapped() {
        assertThat(JitLLMConversions.unwrapToolResult("\"sunny\"")).isEqualTo("sunny");
        assertThat(JitLLMConversions.unwrapToolResult(null)).isEmpty();
    }

    @Test
    void toolSpecificationsCarryNameDescriptionAndSchema() {
        assertThat(JitLLMConversions.toEngineTools(List.of(
                ToolSpecification.builder().name("getWeather").description("the weather").build())))
                .singleElement().satisfies(spec -> {
                    assertThat(spec.name()).isEqualTo("getWeather");
                    assertThat(spec.parametersJsonSchema()).contains("object");
                });
    }

    @Test
    void aBlankToolNameIsRejectedByTheFacadeRatherThanSentOn() {
        assertThatThrownBy(() -> new ToolSpec(" ", "", "{}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tokenUsageReportsWhatTheTurnIngestedAndGenerated() {
        GenerationResult result = new GenerationResult("hi", 12, 3, FinishReason.STOP_TOKEN,
                new GenerationTimings(Duration.ZERO, Duration.ZERO, 12, 3));

        dev.langchain4j.model.output.TokenUsage usage = JitLLMConversions.toTokenUsage(result);
        assertThat(usage.inputTokenCount()).isEqualTo(12);
        assertThat(usage.outputTokenCount()).isEqualTo(3);
        assertThat(usage.totalTokenCount()).isEqualTo(15);
    }
}
