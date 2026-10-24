package io.quarkiverse.langchain4j.mockapi.internal.dialect.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.ChatRequestView;
import io.quarkiverse.langchain4j.mockapi.EmbeddingReply;
import io.quarkiverse.langchain4j.mockapi.FinishReason;
import io.quarkiverse.langchain4j.mockapi.Operation;
import io.quarkiverse.langchain4j.mockapi.SseEvent;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Dialect;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.DialectRegistry;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Rendered;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.UnrenderableReply;
import io.quarkiverse.langchain4j.mockapi.internal.json.MockApiJson;
import io.quarkiverse.langchain4j.mockapi.internal.model.CapturedRequestImpl;

class BedrockDialectTest {

    private static final String NOVA = "global.amazon.nova-2-lite-v1:0";
    private static final Dialect.Match CHAT = new Dialect.Match(Operation.CHAT, false, NOVA);
    private static final Dialect.Match STREAM = new Dialect.Match(Operation.CHAT, true, NOVA);
    private static final Dialect.Match COHERE = new Dialect.Match(Operation.EMBEDDINGS, false, "cohere.embed-english-v3");
    private static final Dialect.Match TITAN_V1 = new Dialect.Match(Operation.EMBEDDINGS, false, "amazon.titan-embed-text-v1");
    private static final Dialect.Match TITAN_V2 = new Dialect.Match(Operation.EMBEDDINGS, false,
            "amazon.titan-embed-text-v2:0");

    BedrockDialect d = new BedrockDialect();
    ObjectMapper m = MockApiJson.mapper();

    static CapturedRequest req(String path, String body) {
        return new CapturedRequestImpl(7, "POST", path, null, Map.of(), body, Operation.CHAT, "bedrock", null, null);
    }

    @Test
    void matchesConverseConverseStreamAndEmbeddingInvocationsUnderItsPrefix() {
        assertThat(d.id()).isEqualTo("bedrock");
        assertThat(d.pathPrefix()).isEqualTo("/bedrock");
        // the model id is a URI label, so its colon arrives percent-encoded
        assertThat(d.match("POST", "/model/global.amazon.nova-2-lite-v1%3A0/converse", Map.of(), null)).contains(CHAT);
        assertThat(d.match("POST", "/model/global.amazon.nova-2-lite-v1%3A0/converse-stream", Map.of(), null))
                .contains(STREAM);
        assertThat(d.match("POST", "/model/cohere.embed-english-v3/invoke", Map.of(), json("{\"texts\":[\"a\"]}")))
                .contains(COHERE);
        assertThat(d.match("POST", "/model/amazon.titan-embed-text-v2%3A0/invoke", Map.of(), json("{\"inputText\":\"a\"}")))
                .contains(TITAN_V2);

        // other model invocations (a model's native chat body) and other operations are left to raw stubs
        assertThat(d.match("POST", "/model/anthropic.claude/invoke", Map.of(), json("{\"messages\":[]}"))).isEmpty();
        assertThat(d.match("POST", "/model/anthropic.claude/invoke", Map.of(), null)).isEmpty();
        assertThat(d.match("POST", "/model/m/invoke-with-response-stream", Map.of(), json("{\"texts\":[]}"))).isEmpty();
        assertThat(d.match("GET", "/model/m/converse", Map.of(), null)).isEmpty();
        assertThat(d.match("POST", "/guardrail/g/version/1/apply", Map.of(), null)).isEmpty();
    }

    @Test
    void bedrockRequestsAreResolvedByPrefix() {
        assertThat(DialectRegistry.load().resolve("POST", "/bedrock/model/m/converse", Map.of(), null))
                .get().extracting(r -> r.dialect().id()).isEqualTo("bedrock");
    }

    @Test
    void parsesConverseRequest() {
        ChatRequestView v = d.parseChat(CHAT, json("""
                {"system":[{"text":"be nice"},{"cachePoint":{"type":"default"}},{"text":" and short"}],
                 "messages":[
                   {"role":"user","content":[{"text":"weather "},{"image":{"format":"png"}},{"text":"in Paris?"}]},
                   {"role":"assistant","content":[{"text":"checking"},
                     {"toolUse":{"toolUseId":"t1","name":"getWeather","input":{"city":"Paris"}}}]},
                   {"role":"user","content":[
                     {"toolResult":{"toolUseId":"t1","content":[{"text":"rainy"}]}},
                     {"toolResult":{"toolUseId":"t2","content":[{"json":{"temp":12}}],"status":"error"}}]}],
                 "inferenceConfig":{"maxTokens":100,"temperature":0.3,"topP":0.9},
                 "toolConfig":{"tools":[{"toolSpec":{"name":"getWeather","description":"weather",
                   "inputSchema":{"json":{"type":"object","properties":{"city":{"type":"string"}}}}}},
                   {"cachePoint":{"type":"default"}}],
                   "toolChoice":{"tool":{"name":"getWeather"}}},
                 "outputConfig":{"textFormat":{"type":"json_schema",
                   "structure":{"jsonSchema":{"name":"s","schema":"{\\"type\\":\\"object\\"}"}}}}}
                """));

        assertThat(v.modelName()).isEqualTo(NOVA);
        assertThat(v.stream()).isFalse();
        assertThat(v.temperature()).isEqualTo(0.3);
        assertThat(v.topP()).isEqualTo(0.9);
        assertThat(v.maxTokens()).isEqualTo(100);
        assertThat(v.messages()).hasSize(5);
        assertThat(v.messages().get(0).role()).isEqualTo("system");
        assertThat(v.messages().get(0).text()).isEqualTo("be nice and short");
        assertThat(v.messages().get(1).role()).isEqualTo("user");
        assertThat(v.messages().get(1).text()).isEqualTo("weather in Paris?");
        assertThat(v.messages().get(2).role()).isEqualTo("assistant");
        assertThat(v.messages().get(2).text()).isEqualTo("checking");
        assertThat(v.messages().get(2).toolCalls()).singleElement().satisfies(tc -> {
            assertThat(tc.id()).isEqualTo("t1");
            assertThat(tc.name()).isEqualTo("getWeather");
            assertThat(tc.argumentsJson()).isEqualTo("{\"city\":\"Paris\"}");
        });
        // each tool result becomes a tool message
        assertThat(v.messages().get(3).role()).isEqualTo("tool");
        assertThat(v.messages().get(3).toolCallId()).isEqualTo("t1");
        assertThat(v.messages().get(3).text()).isEqualTo("rainy");
        assertThat(v.messages().get(4).toolCallId()).isEqualTo("t2");
        assertThat(v.messages().get(4).text()).isEqualTo("{\"temp\":12}");
        assertThat(v.tools()).singleElement().satisfies(t -> {
            assertThat(t.name()).isEqualTo("getWeather");
            assertThat(t.description()).isEqualTo("weather");
            assertThat(t.parametersSchema().at("/properties/city/type").asText()).isEqualTo("string");
        });
        assertThat(v.toolChoice()).isEqualTo("getWeather");
        assertThat(v.responseFormatType()).isEqualTo("json_schema");
        assertThat(v.responseFormatSchema().at("/type").asText()).isEqualTo("object");
    }

    @Test
    void parsesAMinimalStreamingRequest() {
        ChatRequestView v = d.parseChat(STREAM, json("""
                {"system":[],"messages":[{"role":"user","content":[{"text":"hi"}]}],
                 "toolConfig":{"tools":[],"toolChoice":{"any":{}}}}
                """));

        assertThat(v.stream()).isTrue();
        assertThat(v.temperature()).isNull();
        assertThat(v.maxTokens()).isNull();
        assertThat(v.toolChoice()).isEqualTo("any");
        assertThat(v.responseFormatType()).isNull();
        // an empty system list gives no system message
        assertThat(v.messages()).singleElement().satisfies(msg -> {
            assertThat(msg.role()).isEqualTo("user");
            assertThat(msg.text()).isEqualTo("hi");
            assertThat(msg.toolCalls()).isNull();
        });
    }

    @Test
    void rendersConverseText() throws Exception {
        Rendered r = d.render(CHAT, ChatReply.text("Hello").usage(3, 5), req("/bedrock/model/m/converse", "{}"));

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.headers()).containsEntry("Content-Type", "application/json");
        JsonNode j = m.readTree(r.body());
        assertThat(j.at("/output/message/role").asText()).isEqualTo("assistant");
        assertThat(j.at("/output/message/content")).hasSize(1);
        assertThat(j.at("/output/message/content/0/text").asText()).isEqualTo("Hello");
        assertThat(j.at("/stopReason").asText()).isEqualTo("end_turn");
        assertThat(j.at("/usage/inputTokens").asInt()).isEqualTo(3);
        assertThat(j.at("/usage/outputTokens").asInt()).isEqualTo(5);
        assertThat(j.at("/usage/totalTokens").asInt()).isEqualTo(8);
        assertThat(j.at("/usage").has("cacheReadInputTokens")).isFalse();
        assertThat(j.at("/metrics/latencyMs").isNumber()).isTrue();
    }

    @Test
    void rendersConverseToolUseThinkingAndCacheUsage() throws Exception {
        ChatReply reply = ChatReply.toolCall("getWeather", Map.of("city", "Paris"))
                .addToolCall("getTime", "")
                .thinking("let me check");
        Rendered r = d.render(CHAT, reply, req("/bedrock/model/m/converse", "{}"));

        JsonNode j = m.readTree(r.body());
        JsonNode content = j.at("/output/message/content");
        assertThat(content).hasSize(3);
        assertThat(content.at("/0/reasoningContent/reasoningText/text").asText()).isEqualTo("let me check");
        assertThat(content.at("/1/toolUse/toolUseId").asText()).isEqualTo("tooluse_mock_0");
        assertThat(content.at("/1/toolUse/name").asText()).isEqualTo("getWeather");
        assertThat(content.at("/1/toolUse/input/city").asText()).isEqualTo("Paris");
        assertThat(content.at("/2/toolUse/toolUseId").asText()).isEqualTo("tooluse_mock_1");
        // blank arguments become an empty input object
        assertThat(content.at("/2/toolUse/input").isObject()).isTrue();
        assertThat(content.at("/2/toolUse/input")).isEmpty();
        assertThat(j.at("/stopReason").asText()).isEqualTo("tool_use");
        assertThat(j.at("/usage/totalTokens").asInt()).isZero();
    }

    @Test
    void mapsEveryFinishReasonToADocumentedStopReason() throws Exception {
        assertThat(stopReason(FinishReason.STOP)).isEqualTo("end_turn");
        assertThat(stopReason(FinishReason.TOOL_CALLS)).isEqualTo("tool_use");
        assertThat(stopReason(FinishReason.LENGTH)).isEqualTo("max_tokens");
        assertThat(stopReason(FinishReason.CONTENT_FILTER)).isEqualTo("content_filtered");
    }

    private String stopReason(FinishReason reason) throws Exception {
        Rendered r = d.render(CHAT, ChatReply.text("x").finishReason(reason), req("/bedrock/model/m/converse", "{}"));
        return m.readTree(r.body()).at("/stopReason").asText();
    }

    @Test
    void rendersConverseStreamAsEventStreamEvents() throws Exception {
        ChatReply reply = ChatReply.text("Hello there").usage(3, 5);
        Rendered r = d.render(STREAM, reply, req("/bedrock/model/m/converse-stream", "{}"));

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.streamFormat()).isEqualTo(Rendered.StreamFormat.AWS_EVENT_STREAM);
        assertThat(r.streamFormat().contentType()).isEqualTo("application/vnd.amazon.eventstream");
        assertThat(r.events()).extracting(SseEvent::event).containsExactly("messageStart", "contentBlockDelta",
                "contentBlockDelta", "contentBlockStop", "messageStop", "metadata");
        assertThat(data(r, 0).at("/role").asText()).isEqualTo("assistant");
        assertThat(data(r, 1).at("/contentBlockIndex").asInt()).isZero();
        assertThat(data(r, 1).at("/delta/text").asText()).isEqualTo("Hello");
        assertThat(data(r, 2).at("/delta/text").asText()).isEqualTo(" there");
        assertThat(data(r, 3).at("/contentBlockIndex").asInt()).isZero();
        assertThat(data(r, 4).at("/stopReason").asText()).isEqualTo("end_turn");
        assertThat(data(r, 5).at("/usage/inputTokens").asInt()).isEqualTo(3);
        assertThat(data(r, 5).at("/usage/outputTokens").asInt()).isEqualTo(5);
        assertThat(data(r, 5).at("/usage/totalTokens").asInt()).isEqualTo(8);
        assertThat(data(r, 5).at("/metrics/latencyMs").isNumber()).isTrue();
    }

    @Test
    void streamsThinkingAndToolUseAsTheirOwnContentBlocks() throws Exception {
        ChatReply reply = ChatReply.text("Hi").thinking("hmm").addToolCall("getWeather", "{\"city\":\"Paris\"}");
        Rendered r = d.render(STREAM, reply, req("/bedrock/model/m/converse-stream", "{}"));

        assertThat(r.events()).extracting(SseEvent::event).containsExactly("messageStart",
                "contentBlockDelta", "contentBlockStop",
                "contentBlockDelta", "contentBlockStop",
                "contentBlockStart", "contentBlockDelta", "contentBlockStop",
                "messageStop", "metadata");
        assertThat(data(r, 1).at("/contentBlockIndex").asInt()).isZero();
        assertThat(data(r, 1).at("/delta/reasoningContent/text").asText()).isEqualTo("hmm");
        assertThat(data(r, 3).at("/contentBlockIndex").asInt()).isEqualTo(1);
        assertThat(data(r, 3).at("/delta/text").asText()).isEqualTo("Hi");
        assertThat(data(r, 5).at("/contentBlockIndex").asInt()).isEqualTo(2);
        assertThat(data(r, 5).at("/start/toolUse/toolUseId").asText()).isEqualTo("tooluse_mock_0");
        assertThat(data(r, 5).at("/start/toolUse/name").asText()).isEqualTo("getWeather");
        assertThat(data(r, 6).at("/contentBlockIndex").asInt()).isEqualTo(2);
        // the streamed input is the arguments JSON as a string
        assertThat(data(r, 6).at("/delta/toolUse/input").asText()).isEqualTo("{\"city\":\"Paris\"}");
        assertThat(data(r, 7).at("/contentBlockIndex").asInt()).isEqualTo(2);
        assertThat(data(r, 8).at("/stopReason").asText()).isEqualTo("tool_use");
    }

    @Test
    void emptyStreamedReplyHasOnlyTheMessageFrame() {
        Rendered r = d.render(STREAM, ChatReply.text(""), req("/bedrock/model/m/converse-stream", "{}"));

        assertThat(r.events()).extracting(SseEvent::event).containsExactly("messageStart", "messageStop", "metadata");
    }

    @Test
    void rendersCohereEmbeddingsAsFloatArraysWithoutEmbeddingTypes() throws Exception {
        Rendered r = d.render(COHERE, EmbeddingReply.of(new float[] { 1f, 2f }, new float[] { 3f, 4f }),
                req("/bedrock/model/cohere.embed-english-v3/invoke",
                        "{\"texts\":[\"a\",\"b\"],\"input_type\":\"search_document\"}"));

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.headers()).containsEntry("Content-Type", "application/json");
        JsonNode j = m.readTree(r.body());
        assertThat(j.at("/response_type").asText()).isEqualTo("embeddings_floats");
        assertThat(j.at("/embeddings/0/0").floatValue()).isEqualTo(1f);
        assertThat(j.at("/embeddings/1/1").floatValue()).isEqualTo(4f);
        assertThat(j.at("/texts")).hasSize(2);
        assertThat(j.at("/texts/1").asText()).isEqualTo("b");
        assertThat(j.at("/id").asText()).isNotBlank();
    }

    @Test
    void rendersCohereEmbeddingsByTypeWhenFloatIsRequested() throws Exception {
        Rendered r = d.render(COHERE, EmbeddingReply.derived(4),
                req("/bedrock/model/cohere.embed-english-v3/invoke",
                        "{\"texts\":[\"a\",\"b\"],\"input_type\":\"search_query\",\"embedding_types\":[\"float\"]}"));

        JsonNode j = m.readTree(r.body());
        assertThat(j.at("/response_type").asText()).isEqualTo("embeddings_by_type");
        assertThat(j.at("/embeddings/float")).hasSize(2);
        assertThat(j.at("/embeddings/float/0")).hasSize(4);
    }

    @Test
    void tooFewCohereVectorsIsUnrenderable() {
        assertThatThrownBy(() -> d.render(COHERE, EmbeddingReply.of(new float[] { 1f }),
                req("/bedrock/model/cohere.embed-english-v3/invoke", "{\"texts\":[\"a\",\"b\"]}")))
                .isInstanceOf(UnrenderableReply.class).hasMessageContaining("2 inputs");
    }

    @Test
    void rendersTitanEmbeddingOfTheOneInput() throws Exception {
        Rendered r = d.render(TITAN_V1, EmbeddingReply.derived(3),
                req("/bedrock/model/amazon.titan-embed-text-v1/invoke", "{\"inputText\":\"hello world\"}"));

        JsonNode j = m.readTree(r.body());
        assertThat(j.at("/embedding")).hasSize(3);
        assertThat(j.at("/inputTextTokenCount").asInt()).isEqualTo(2);
        // only Titan Text Embeddings V2 documents embeddingsByType
        assertThat(j.has("embeddingsByType")).isFalse();
    }

    @Test
    void rendersTitanV2EmbeddingAlsoByType() throws Exception {
        Rendered r = d.render(TITAN_V2, EmbeddingReply.of(new float[] { 1f, 2f }, new float[] { 3f, 4f }),
                req("/bedrock/model/amazon.titan-embed-text-v2%3A0/invoke", "{\"inputText\":\"hi\",\"normalize\":true}"));

        JsonNode j = m.readTree(r.body());
        // one input per request, so the first vector is used
        assertThat(j.at("/embedding/0").floatValue()).isEqualTo(1f);
        assertThat(j.at("/embeddingsByType/float/1").floatValue()).isEqualTo(2f);
        assertThat(j.at("/inputTextTokenCount").asInt()).isEqualTo(1);
    }

    @Test
    void rendersErrorsWithTheErrorTypeHeader() throws Exception {
        assertError(400, "ValidationException");
        assertError(403, "AccessDeniedException");
        assertError(404, "ResourceNotFoundException");
        assertError(408, "ModelTimeoutException");
        assertError(424, "ModelErrorException");
        assertError(429, "ThrottlingException");
        assertError(500, "InternalServerException");
        assertError(503, "ServiceUnavailableException");
        // a common error of all AWS services
        assertError(401, "NotAuthorized");
        assertError(413, "ValidationException");
        assertError(502, "InternalServerException");
    }

    private void assertError(int status, String type) throws Exception {
        Rendered r = d.renderError(STREAM, status, "boom");

        assertThat(r.status()).isEqualTo(status);
        assertThat(r.streamFormat()).isEqualTo(Rendered.StreamFormat.NONE);
        assertThat(r.headers()).containsEntry("Content-Type", "application/json").containsEntry("x-amzn-ErrorType", type);
        JsonNode j = m.readTree(r.body());
        assertThat(j.at("/message").asText()).isEqualTo("boom");
        assertThat(j.size()).isEqualTo(1);
    }

    private JsonNode data(Rendered r, int i) throws Exception {
        return m.readTree(r.events().get(i).data());
    }

    private JsonNode json(String s) {
        try {
            return m.readTree(s);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Test
    void invalidToolCallArgumentsAreUnrenderableNamingTheTool() {
        assertThatThrownBy(() -> d.render(CHAT, ChatReply.toolCall("getWeather", "{not json"),
                req("/bedrock/model/" + NOVA + "/converse", "{}")))
                .isInstanceOf(UnrenderableReply.class).hasMessageContaining("getWeather");
    }
}
