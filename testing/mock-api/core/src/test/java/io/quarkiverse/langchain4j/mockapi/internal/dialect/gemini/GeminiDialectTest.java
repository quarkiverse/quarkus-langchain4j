package io.quarkiverse.langchain4j.mockapi.internal.dialect.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
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
import io.quarkiverse.langchain4j.mockapi.ToolCall;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Dialect;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.DialectRegistry;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Rendered;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.UnrenderableReply;
import io.quarkiverse.langchain4j.mockapi.internal.json.MockApiJson;
import io.quarkiverse.langchain4j.mockapi.internal.model.CapturedRequestImpl;
import io.quarkiverse.langchain4j.mockapi.internal.model.ChatReplyImpl;

class GeminiDialectTest {

    private static final String MODEL = "gemini-2.5-flash";
    private static final Dialect.Match CHAT = new Dialect.Match(Operation.CHAT, false, MODEL);
    private static final Dialect.Match STREAM = new Dialect.Match(Operation.CHAT, true, MODEL);
    private static final Dialect.Match EMBED = new Dialect.Match(Operation.EMBEDDINGS, false, "text-embedding-004");

    GeminiDialect d = new GeminiDialect("google-genai", "/google-genai");
    ObjectMapper m = MockApiJson.mapper();

    static CapturedRequest req(String path, String body) {
        return new CapturedRequestImpl(3, "POST", "/google-genai" + path, null, Map.of(), body, Operation.CHAT,
                "google-genai", null, null);
    }

    static CapturedRequest chatReq() {
        return req("/v1beta/models/" + MODEL + ":generateContent", "{}");
    }

    @Test
    void twoProvidersWithTheirOwnPrefixes() {
        assertThat(DialectRegistry.load().dialects()).filteredOn(GeminiDialect.class::isInstance)
                .extracting(Dialect::id, Dialect::pathPrefix)
                .contains(tuple("google-genai", "/google-genai"), tuple("ai-gemini", "/ai-gemini"));
    }

    @Test
    void matchesModelMethods() {
        Map<String, List<String>> sse = Map.of("alt", List.of("sse"));
        String base = "/v1beta/models/" + MODEL;
        assertThat(d.match("POST", base + ":generateContent", Map.of(), null)).contains(CHAT);
        assertThat(d.match("POST", base + ":streamGenerateContent", sse, null)).contains(STREAM);
        assertThat(d.match("POST", "/v1beta/models/text-embedding-004:embedContent", Map.of(), null)).contains(EMBED);
        assertThat(d.match("POST", "/v1beta/models/text-embedding-004:batchEmbedContents", Map.of(), null))
                .contains(EMBED);
        // only the SSE flavour of streaming is supported
        assertThat(d.match("POST", base + ":streamGenerateContent", Map.of(), null)).isEmpty();
        assertThat(d.match("GET", base + ":generateContent", Map.of(), null)).isEmpty();
        assertThat(d.match("POST", base + ":countTokens", Map.of(), null)).isEmpty();
        assertThat(d.match("POST", "/v1/chat/completions", Map.of(), null)).isEmpty();
    }

    @Test
    void parsesRequest() throws Exception {
        String body = """
                {"systemInstruction":{"parts":[{"text":"be "},{"text":"nice"}]},
                 "contents":[{"role":"user","parts":[{"text":"hi"}]},
                             {"role":"model","parts":[{"text":"hmm","thought":true},{"text":"let me check"},
                                {"functionCall":{"id":"c1","name":"getWeather","args":{"city":"Paris"}}}]},
                             {"role":"user","parts":[{"functionResponse":{"id":"c1","name":"getWeather",
                                "response":{"result":"rainy"}}}]},
                             {"role":"user","parts":[{"text":"thanks"}]}],
                 "tools":[{"functionDeclarations":[{"name":"getWeather","description":"weather",
                    "parameters":{"type":"OBJECT"}}]}],
                 "toolConfig":{"functionCallingConfig":{"mode":"ANY","allowedFunctionNames":["getWeather"]}},
                 "generationConfig":{"temperature":0.2,"topP":0.7,"maxOutputTokens":100,
                    "responseMimeType":"application/json","responseSchema":{"type":"OBJECT"}}}
                """;
        ChatRequestView v = d.parseChat(CHAT, m.readTree(body));
        assertThat(v.modelName()).isEqualTo(MODEL);
        assertThat(v.stream()).isFalse();
        assertThat(v.temperature()).isEqualTo(0.2);
        assertThat(v.topP()).isEqualTo(0.7);
        assertThat(v.maxTokens()).isEqualTo(100);
        assertThat(v.responseFormatType()).isEqualTo("application/json");
        assertThat(v.responseFormatSchema().path("type").asText()).isEqualTo("OBJECT");
        assertThat(v.toolChoice()).isEqualTo("getWeather");
        assertThat(v.tools()).singleElement().satisfies(t -> {
            assertThat(t.name()).isEqualTo("getWeather");
            assertThat(t.description()).isEqualTo("weather");
            assertThat(t.parametersSchema().path("type").asText()).isEqualTo("OBJECT");
        });
        List<ChatRequestView.Message> msgs = v.messages();
        assertThat(msgs).extracting(ChatRequestView.Message::role)
                .containsExactly("system", "user", "assistant", "tool", "user");
        assertThat(msgs.get(0).text()).isEqualTo("be nice");
        assertThat(msgs.get(1).text()).isEqualTo("hi");
        assertThat(msgs.get(2).text()).isEqualTo("let me check");
        assertThat(msgs.get(2).toolCalls()).singleElement().satisfies(tc -> {
            assertThat(tc.id()).isEqualTo("c1");
            assertThat(tc.name()).isEqualTo("getWeather");
            assertThat(m.readTree(tc.argumentsJson())).isEqualTo(m.readTree("{\"city\":\"Paris\"}"));
        });
        assertThat(msgs.get(3).toolCallId()).isEqualTo("c1");
        assertThat(msgs.get(3).name()).isEqualTo("getWeather");
        assertThat(m.readTree(msgs.get(3).text())).isEqualTo(m.readTree("{\"result\":\"rainy\"}"));
        assertThat(msgs.get(4).text()).isEqualTo("thanks");
    }

    @Test
    void parsesJsonSchemaParametersAndPlainToolMode() throws Exception {
        String body = """
                {"contents":[{"parts":[{"text":"hi"}]}],
                 "tools":[{"functionDeclarations":[{"name":"f","parametersJsonSchema":{"type":"object"}}]}],
                 "toolConfig":{"functionCallingConfig":{"mode":"AUTO"}},
                 "generationConfig":{"responseJsonSchema":{"type":"object"}}}
                """;
        ChatRequestView v = d.parseChat(STREAM, m.readTree(body));
        assertThat(v.stream()).isTrue();
        assertThat(v.toolChoice()).isEqualTo("auto");
        assertThat(v.tools()).singleElement()
                .satisfies(t -> assertThat(t.parametersSchema().path("type").asText()).isEqualTo("object"));
        assertThat(v.responseFormatSchema().path("type").asText()).isEqualTo("object");
        assertThat(v.messages()).singleElement().satisfies(msg -> {
            assertThat(msg.role()).isEqualTo("user");
            assertThat(msg.text()).isEqualTo("hi");
        });
        assertThat(v.temperature()).isNull();
        assertThat(v.maxTokens()).isNull();
    }

    @Test
    void rendersTextReply() throws Exception {
        Rendered r = d.render(CHAT, ChatReply.text("hello").usage(14, 47), chatReq());
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.headers()).containsEntry("Content-Type", "application/json");
        JsonNode j = m.readTree(r.body());
        assertThat(j.at("/candidates")).hasSize(1);
        assertThat(j.at("/candidates/0/index").asInt()).isZero();
        assertThat(j.at("/candidates/0/content/role").asText()).isEqualTo("model");
        assertThat(j.at("/candidates/0/content/parts")).hasSize(1);
        assertThat(j.at("/candidates/0/content/parts/0/text").asText()).isEqualTo("hello");
        assertThat(j.at("/candidates/0/finishReason").asText()).isEqualTo("STOP");
        assertThat(j.at("/usageMetadata/promptTokenCount").asInt()).isEqualTo(14);
        assertThat(j.at("/usageMetadata/candidatesTokenCount").asInt()).isEqualTo(47);
        assertThat(j.at("/usageMetadata/totalTokenCount").asInt()).isEqualTo(61);
        assertThat(j.at("/modelVersion").asText()).isEqualTo(MODEL);
        assertThat(j.at("/responseId").asText()).isEqualTo("mock-response-3");
    }

    @Test
    void mapsFinishReasons() throws Exception {
        assertThat(finishReason(ChatReply.text("x").finishReason(FinishReason.LENGTH))).isEqualTo("MAX_TOKENS");
        assertThat(finishReason(ChatReply.text("x").finishReason(FinishReason.CONTENT_FILTER))).isEqualTo("SAFETY");
        // Gemini ends a turn with function calls with STOP
        assertThat(finishReason(ChatReply.toolCall("f", "{}"))).isEqualTo("STOP");
    }

    private String finishReason(ChatReply reply) throws Exception {
        return m.readTree(d.render(CHAT, reply, chatReq()).body()).at("/candidates/0/finishReason").asText();
    }

    @Test
    void rendersFunctionCallWithObjectArgsAndThought() throws Exception {
        ChatReply reply = ChatReply.toolCall("getWeather", Map.of("city", "Paris")).thinking("hmm").modelName("x-model")
                .id("resp-1");
        JsonNode j = m.readTree(d.render(CHAT, reply, chatReq()).body());
        assertThat(j.at("/modelVersion").asText()).isEqualTo("x-model");
        assertThat(j.at("/responseId").asText()).isEqualTo("resp-1");
        JsonNode parts = j.at("/candidates/0/content/parts");
        assertThat(parts).hasSize(2);
        assertThat(parts.at("/0/thought").asBoolean()).isTrue();
        assertThat(parts.at("/0/text").asText()).isEqualTo("hmm");
        assertThat(parts.at("/1/functionCall/name").asText()).isEqualTo("getWeather");
        assertThat(parts.at("/1/functionCall/args").isObject()).isTrue();
        assertThat(parts.at("/1/functionCall/args/city").asText()).isEqualTo("Paris");
        // the id is optional in Gemini and only sent when the reply has one
        assertThat(parts.at("/1/functionCall").has("id")).isFalse();
    }

    @Test
    void rendersExplicitFunctionCallId() throws Exception {
        ChatReply reply = new ChatReplyImpl(null, List.of(ToolCall.of("c9", "f", "{}")), null, null, null, null, null,
                null, null);
        JsonNode j = m.readTree(d.render(CHAT, reply, chatReq()).body());
        assertThat(j.at("/candidates/0/content/parts/0/functionCall/id").asText()).isEqualTo("c9");
    }

    @Test
    void rendersErrorEnvelope() throws Exception {
        Rendered r = d.renderError(CHAT, 429, "rate limited");
        assertThat(r.status()).isEqualTo(429);
        JsonNode j = m.readTree(r.body());
        assertThat(j.at("/error/code").asInt()).isEqualTo(429);
        assertThat(j.at("/error/message").asText()).isEqualTo("rate limited");
        assertThat(j.at("/error/status").asText()).isEqualTo("RESOURCE_EXHAUSTED");

        assertThat(errorStatus(400)).isEqualTo("INVALID_ARGUMENT");
        assertThat(errorStatus(401)).isEqualTo("UNAUTHENTICATED");
        assertThat(errorStatus(403)).isEqualTo("PERMISSION_DENIED");
        assertThat(errorStatus(404)).isEqualTo("NOT_FOUND");
        assertThat(errorStatus(418)).isEqualTo("FAILED_PRECONDITION");
        assertThat(errorStatus(500)).isEqualTo("INTERNAL");
        assertThat(errorStatus(503)).isEqualTo("UNAVAILABLE");
        assertThat(errorStatus(504)).isEqualTo("DEADLINE_EXCEEDED");
    }

    private String errorStatus(int status) throws Exception {
        return m.readTree(d.renderError(CHAT, status, "x").body()).at("/error/status").asText();
    }

    @Test
    void streamsTextChunksWithFinishReasonAndUsageOnTheLast() throws Exception {
        Rendered r = d.render(STREAM, ChatReply.text("hi there").usage(5, 7), chatReq());
        assertThat(r.streamFormat()).isEqualTo(Rendered.StreamFormat.SSE);
        List<SseEvent> events = r.events();
        assertThat(events).hasSize(2);
        assertThat(events).extracting(SseEvent::event).containsOnlyNulls();
        JsonNode first = m.readTree(events.get(0).data());
        assertThat(first.at("/candidates/0/content/role").asText()).isEqualTo("model");
        assertThat(first.at("/candidates/0/content/parts/0/text").asText()).isEqualTo("hi");
        assertThat(first.at("/candidates/0").has("finishReason")).isFalse();
        assertThat(first.has("usageMetadata")).isFalse();
        assertThat(first.at("/modelVersion").asText()).isEqualTo(MODEL);
        assertThat(first.at("/responseId").asText()).isEqualTo("mock-response-3");
        JsonNode last = m.readTree(events.get(1).data());
        assertThat(last.at("/candidates/0/content/parts/0/text").asText()).isEqualTo(" there");
        assertThat(last.at("/candidates/0/finishReason").asText()).isEqualTo("STOP");
        assertThat(last.at("/usageMetadata/promptTokenCount").asInt()).isEqualTo(5);
        assertThat(last.at("/usageMetadata/candidatesTokenCount").asInt()).isEqualTo(7);
        assertThat(last.at("/usageMetadata/totalTokenCount").asInt()).isEqualTo(12);
    }

    @Test
    void streamsThoughtThenTextThenFunctionCall() throws Exception {
        ChatReply reply = ChatReply.text("ok").addToolCall("getWeather", "{\"city\":\"Paris\"}").thinking("hmm");
        List<SseEvent> events = d.render(STREAM, reply, chatReq()).events();
        assertThat(events).hasSize(3);
        JsonNode thought = m.readTree(events.get(0).data()).at("/candidates/0/content/parts/0");
        assertThat(thought.path("thought").asBoolean()).isTrue();
        assertThat(thought.path("text").asText()).isEqualTo("hmm");
        assertThat(m.readTree(events.get(1).data()).at("/candidates/0/content/parts/0/text").asText()).isEqualTo("ok");
        JsonNode last = m.readTree(events.get(2).data());
        assertThat(last.at("/candidates/0/content/parts/0/functionCall/name").asText()).isEqualTo("getWeather");
        assertThat(last.at("/candidates/0/content/parts/0/functionCall/args/city").asText()).isEqualTo("Paris");
        assertThat(last.at("/candidates/0/finishReason").asText()).isEqualTo("STOP");
        assertThat(last.has("usageMetadata")).isTrue();
    }

    @Test
    void streamsOneEmptyTextChunkWhenReplyHasNoContent() throws Exception {
        List<SseEvent> events = d.render(STREAM, ChatReply.text(""), chatReq()).events();
        assertThat(events).singleElement().satisfies(e -> {
            JsonNode j = m.readTree(e.data());
            assertThat(j.at("/candidates/0/content/parts/0/text").asText()).isEmpty();
            assertThat(j.at("/candidates/0/finishReason").asText()).isEqualTo("STOP");
        });
    }

    @Test
    void rendersSingleEmbedding() throws Exception {
        CapturedRequest r = req("/v1beta/models/text-embedding-004:embedContent",
                "{\"content\":{\"parts\":[{\"text\":\"a\"}]}}");
        JsonNode j = m.readTree(d.render(EMBED, EmbeddingReply.of(new float[] { 1, 0 }), r).body());
        assertThat(j.at("/embedding/values")).hasSize(2);
        assertThat(j.at("/embedding/values/0").floatValue()).isEqualTo(1f);
    }

    @Test
    void rendersBatchEmbeddingsInRequestOrder() throws Exception {
        CapturedRequest r = req("/v1beta/models/text-embedding-004:batchEmbedContents", """
                {"requests":[{"model":"models/text-embedding-004","content":{"parts":[{"text":"a"}]}},
                             {"model":"models/text-embedding-004","content":{"parts":[{"text":"b"}]}}]}
                """);
        JsonNode j = m.readTree(d.render(EMBED, EmbeddingReply.of(new float[] { 1, 0 }, new float[] { 0, 1 }), r).body());
        assertThat(j.at("/embeddings")).hasSize(2);
        assertThat(j.at("/embeddings/1/values/1").floatValue()).isEqualTo(1f);

        JsonNode derived = m.readTree(d.render(EMBED, EmbeddingReply.derived(3), r).body());
        assertThat(derived.at("/embeddings")).hasSize(2);
        assertThat(derived.at("/embeddings/0/values")).hasSize(3);
    }

    @Test
    void tooFewEmbeddingVectorsIsAnError() {
        CapturedRequest r = req("/v1beta/models/text-embedding-004:batchEmbedContents",
                "{\"requests\":[{\"content\":{\"parts\":[{\"text\":\"a\"}]}},{\"content\":{\"parts\":[{\"text\":\"b\"}]}}]}");
        assertThatThrownBy(() -> d.render(EMBED, EmbeddingReply.of(new float[] { 1 }), r))
                .isInstanceOf(UnrenderableReply.class).hasMessageContaining("2 inputs");
    }

    @Test
    void invalidToolCallArgumentsAreUnrenderableNamingTheTool() {
        assertThatThrownBy(() -> d.render(CHAT, ChatReply.toolCall("getWeather", "{not json"), chatReq()))
                .isInstanceOf(UnrenderableReply.class).hasMessageContaining("getWeather");
    }
}
