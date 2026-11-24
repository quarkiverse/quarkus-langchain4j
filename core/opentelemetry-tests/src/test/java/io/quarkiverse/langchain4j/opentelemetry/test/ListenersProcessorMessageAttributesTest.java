package io.quarkiverse.langchain4j.opentelemetry.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.KeyValue;
import io.opentelemetry.api.common.Value;
import io.quarkus.test.QuarkusExtensionTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

class ListenersProcessorMessageAttributesTest extends ListenersProcessorAbstractSpanChatModelListenerTest {

    private static final AttributeKey<Value<?>> GEN_AI_SYSTEM_INSTRUCTIONS = AttributeKey
            .valueKey("gen_ai.system_instructions");
    private static final AttributeKey<Value<?>> GEN_AI_INPUT_MESSAGES = AttributeKey.valueKey("gen_ai.input.messages");
    private static final AttributeKey<Value<?>> GEN_AI_OUTPUT_MESSAGES = AttributeKey.valueKey("gen_ai.output.messages");
    private static final AttributeKey<String> GEN_AI_RESPONSE_FINISH_REASONS = AttributeKey
            .stringKey("gen_ai.response.finish_reasons");
    private static final AttributeKey<String> GEN_AI_PROMPT = AttributeKey.stringKey("gen_ai.prompt");
    private static final AttributeKey<String> GEN_AI_COMPLETION = AttributeKey.stringKey("gen_ai.completion");

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private static final ToolExecutionRequest WEATHER_TOOL_CALL = ToolExecutionRequest.builder()
            .id("call-1")
            .name("getWeather")
            .arguments("{\"city\":\"Paris\"}")
            .build();

    private static final List<ChatMessage> CONVERSATION = List.of(
            SystemMessage.from("You are a poet"),
            UserMessage.from("Write a poem about the weather"),
            AiMessage.from(WEATHER_TOOL_CALL),
            ToolExecutionResultMessage.from(WEATHER_TOOL_CALL, "sunny"));

    private static final ChatResponse POEM_RESPONSE = ChatResponse.builder()
            .aiMessage(AiMessage.from("The sun is shining"))
            .finishReason(FinishReason.STOP)
            .build();

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> appWithInMemorySpanExporter())
            .overrideConfigKey("quarkus.langchain4j.tracing.include-prompt", "true")
            .overrideConfigKey("quarkus.langchain4j.tracing.include-completion", "true");

    @Test
    void shouldKeepSystemMessageInChatHistoryWhenProviderSendsItThere() {
        var attributes = recordSpan(ModelProvider.OPEN_AI, CONVERSATION, POEM_RESPONSE);

        assertThat(attributes.get(GEN_AI_SYSTEM_INSTRUCTIONS))
                .isNull();
        assertThat(json(attributes, GEN_AI_INPUT_MESSAGES))
                .isEqualTo(json("""
                        [
                          {"role": "system", "parts": [{"type": "text", "content": "You are a poet"}]},
                          {"role": "user", "parts": [{"type": "text", "content": "Write a poem about the weather"}]},
                          {"role": "assistant", "parts": [
                            {"type": "tool_call", "id": "call-1", "name": "getWeather", "arguments": {"city": "Paris"}}
                          ]},
                          {"role": "tool", "parts": [{"type": "tool_call_response", "id": "call-1", "response": "sunny"}]}
                        ]
                        """));
        assertThat(json(attributes, GEN_AI_OUTPUT_MESSAGES))
                .isEqualTo(json("""
                        [
                          {"role": "assistant", "parts": [{"type": "text", "content": "The sun is shining"}]}
                        ]
                        """));
        assertThat(attributes.get(GEN_AI_RESPONSE_FINISH_REASONS))
                .isEqualTo("STOP");
        assertThat(attributes.get(GEN_AI_PROMPT))
                .startsWith("You are a poet\nWrite a poem about the weather")
                .endsWith("sunny");
        assertThat(attributes.get(GEN_AI_COMPLETION))
                .isEqualTo("The sun is shining");
    }

    @Test
    void shouldRecordSystemInstructionsSeparatelyWhenProviderSendsThemSeparately() {
        var attributes = recordSpan(ModelProvider.ANTHROPIC, CONVERSATION, POEM_RESPONSE);

        assertThat(json(attributes, GEN_AI_SYSTEM_INSTRUCTIONS))
                .isEqualTo(json("""
                        [{"type": "text", "content": "You are a poet"}]
                        """));
        assertThat(json(attributes, GEN_AI_INPUT_MESSAGES))
                .isEqualTo(json("""
                        [
                          {"role": "user", "parts": [{"type": "text", "content": "Write a poem about the weather"}]},
                          {"role": "assistant", "parts": [
                            {"type": "tool_call", "id": "call-1", "name": "getWeather", "arguments": {"city": "Paris"}}
                          ]},
                          {"role": "tool", "parts": [{"type": "tool_call_response", "id": "call-1", "response": "sunny"}]}
                        ]
                        """));
    }

    @Test
    void shouldRecordMultipartToolResult() {
        var toolResult = ToolExecutionResultMessage.builder()
                .id("call-1")
                .toolName("getWeather")
                .contents(TextContent.from("sunny"), TextContent.from("22 degrees"))
                .build();

        var attributes = recordSpan(ModelProvider.OPEN_AI,
                List.of(UserMessage.from("What is the weather in Paris?"), AiMessage.from(WEATHER_TOOL_CALL), toolResult),
                POEM_RESPONSE);

        assertThat(json(attributes, GEN_AI_INPUT_MESSAGES))
                .isEqualTo(json("""
                        [
                          {"role": "user", "parts": [{"type": "text", "content": "What is the weather in Paris?"}]},
                          {"role": "assistant", "parts": [
                            {"type": "tool_call", "id": "call-1", "name": "getWeather", "arguments": {"city": "Paris"}}
                          ]},
                          {"role": "tool", "parts": [
                            {"type": "tool_call_response", "id": "call-1", "response": [
                              {"type": "text", "content": "sunny"},
                              {"type": "text", "content": "22 degrees"}
                            ]}
                          ]}
                        ]
                        """));
        assertThat(attributes.get(GEN_AI_PROMPT))
                .startsWith("What is the weather in Paris?")
                .endsWith("sunny\n22 degrees");
    }

    @Test
    void shouldRecordInlineImageAsBytes() {
        var imageBytes = new byte[] { (byte) 0x89, 'P', 'N', 'G', 0, 1, 2, (byte) 0xff };
        var userMessage = UserMessage.from(
                TextContent.from("Describe this image"),
                ImageContent.from(Base64.getEncoder().encodeToString(imageBytes), "image/png"));

        var attributes = recordSpan(ModelProvider.OPEN_AI, List.of(userMessage), POEM_RESPONSE);

        var expected = json("""
                [
                  {"role": "user", "parts": [
                    {"type": "text", "content": "Describe this image"},
                    {"type": "blob", "modality": "image", "mime_type": "image/png"}
                  ]}
                ]
                """);
        ((ObjectNode) expected.at("/0/parts/1")).put("content", imageBytes);

        assertThat(json(attributes, GEN_AI_INPUT_MESSAGES))
                .isEqualTo(expected);
    }

    @Test
    void shouldRecordToolCallInOutputMessages() {
        var attributes = recordSpan(ModelProvider.OPEN_AI,
                List.of(UserMessage.from("What is the weather in Paris?")),
                ChatResponse.builder()
                        .aiMessage(AiMessage.from(WEATHER_TOOL_CALL))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build());

        assertThat(json(attributes, GEN_AI_OUTPUT_MESSAGES))
                .isEqualTo(json("""
                        [
                          {"role": "assistant", "parts": [
                            {"type": "tool_call", "id": "call-1", "name": "getWeather", "arguments": {"city": "Paris"}}
                          ]}
                        ]
                        """));
        assertThat(attributes.get(GEN_AI_RESPONSE_FINISH_REASONS))
                .isEqualTo("TOOL_EXECUTION");
        assertThat(attributes.get(GEN_AI_COMPLETION))
                .isNull();
    }

    @Test
    void shouldNotRecordFinishReasonWhenProviderDoesNotSupplyOne() {
        var attributes = recordSpan(ModelProvider.OPEN_AI,
                List.of(UserMessage.from("Write a poem about the weather")),
                ChatResponse.builder()
                        .aiMessage(AiMessage.from("The sun is shining"))
                        .build());

        assertThat(json(attributes, GEN_AI_OUTPUT_MESSAGES))
                .isEqualTo(json("""
                        [
                          {"role": "assistant", "parts": [{"type": "text", "content": "The sun is shining"}]}
                        ]
                        """));
        assertThat(attributes.get(GEN_AI_RESPONSE_FINISH_REASONS))
                .isNull();
    }

    private Attributes recordSpan(ModelProvider modelProvider, List<ChatMessage> messages, ChatResponse response) {
        var contextAttributes = new HashMap<>();
        var request = ChatRequest.builder()
                .messages(messages)
                .parameters(DefaultChatRequestParameters.builder()
                        .modelName("--mock-model-name--")
                        .build())
                .build();

        spanChatModelListener.onRequest(new ChatModelRequestContext(request, modelProvider, contextAttributes));
        spanChatModelListener.onResponse(
                new ChatModelResponseContext(response, request, modelProvider, contextAttributes));

        await().untilAsserted(() -> assertThat(exporter.getFinishedSpanItems()).hasSize(1));
        return exporter.getFinishedSpanItems()
                .get(0)
                .getAttributes();
    }

    /**
     * Reads a structured attribute as JSON so that it can be compared with the semconv message schemas regardless of
     * the order of the fields. Looking the attribute up with a {@code valueKey} also asserts that it was recorded as an
     * {@code AnyValue} rather than as a JSON string. Bytes become binary nodes, so they only match an expected binary
     * node and never a base64 string.
     */
    private static JsonNode json(Attributes attributes, AttributeKey<Value<?>> key) {
        Value<?> value = attributes.get(key);
        assertThat(value)
                .as("%s recorded as a structured value", key.getKey())
                .isNotNull();
        return json(value);
    }

    @SuppressWarnings("unchecked")
    private static JsonNode json(Value<?> value) {
        return switch (value.getType()) {
            case STRING -> NODES.stringNode((String) value.getValue());
            case BOOLEAN -> NODES.booleanNode((Boolean) value.getValue());
            case LONG -> NODES.numberNode((Long) value.getValue());
            case DOUBLE -> NODES.numberNode((Double) value.getValue());
            case ARRAY -> {
                ArrayNode array = NODES.arrayNode();
                for (Value<?> element : (List<Value<?>>) value.getValue()) {
                    array.add(json(element));
                }
                yield array;
            }
            case KEY_VALUE_LIST -> {
                ObjectNode object = NODES.objectNode();
                for (KeyValue field : (List<KeyValue>) value.getValue()) {
                    object.set(field.getKey(), json(field.getValue()));
                }
                yield object;
            }
            case BYTES -> {
                ByteBuffer buffer = ((ByteBuffer) value.getValue()).duplicate();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                yield NODES.binaryNode(bytes);
            }
            case EMPTY -> NODES.nullNode();
        };
    }

    private static JsonNode json(String value) {
        return MAPPER.readTree(value);
    }
}
