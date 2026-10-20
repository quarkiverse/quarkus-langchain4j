package io.quarkiverse.langchain4j.mockapi.internal.dialect.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.ChatRequestView;
import io.quarkiverse.langchain4j.mockapi.EmbeddingReply;
import io.quarkiverse.langchain4j.mockapi.Operation;
import io.quarkiverse.langchain4j.mockapi.SseEvent;
import io.quarkiverse.langchain4j.mockapi.ToolCall;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Dialect;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Rendered;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.UnrenderableReply;
import io.quarkiverse.langchain4j.mockapi.internal.json.MockApiJson;
import io.quarkiverse.langchain4j.mockapi.internal.model.CapturedRequestImpl;
import io.quarkiverse.langchain4j.mockapi.internal.model.ChatReplyImpl;

class VertexAiGeminiDialectTest {

    private static final String MODEL = "gemini-2.5-flash";
    private static final String BASE = "/v1/projects/mock-project/locations/us-central1/publishers/google/models/";
    private static final Dialect.Match CHAT = new Dialect.Match(Operation.CHAT, false, MODEL);
    private static final Dialect.Match STREAM = new Dialect.Match(Operation.CHAT, true, MODEL);
    private static final Dialect.Match EMBED = new Dialect.Match(Operation.EMBEDDINGS, false, "text-embedding-004");

    VertexAiGeminiDialect d = new VertexAiGeminiDialect();
    ObjectMapper m = MockApiJson.mapper();

    static CapturedRequest req(String path, String body) {
        return new CapturedRequestImpl(2, "POST", "/vertexai-gemini" + path, null, Map.of(), body, Operation.CHAT,
                "vertexai-gemini", null, null);
    }

    @Test
    void hasItsOwnIdAndPrefix() {
        assertThat(d.id()).isEqualTo("vertexai-gemini");
        assertThat(d.pathPrefix()).isEqualTo("/vertexai-gemini");
    }

    @Test
    void matchesVertexModelMethods() {
        Map<String, List<String>> sse = Map.of("alt", List.of("sse"));
        assertThat(d.match("POST", BASE + MODEL + ":generateContent", Map.of(), null)).contains(CHAT);
        assertThat(d.match("POST", BASE + MODEL + ":streamGenerateContent", sse, null)).contains(STREAM);
        assertThat(d.match("POST", BASE + "text-embedding-004:predict", Map.of(), null)).contains(EMBED);
        assertThat(d.match("POST", "/v1/projects/p/locations/l/publishers/other/models/m:generateContent", Map.of(), null))
                .contains(new Dialect.Match(Operation.CHAT, false, "m"));
        // only the SSE flavour of streaming is supported
        assertThat(d.match("POST", BASE + MODEL + ":streamGenerateContent", Map.of(), null)).isEmpty();
        assertThat(d.match("GET", BASE + MODEL + ":generateContent", Map.of(), null)).isEmpty();
        assertThat(d.match("POST", BASE + MODEL + ":countTokens", Map.of(), null)).isEmpty();
        // the Gemini API paths belong to the other Gemini dialects
        assertThat(d.match("POST", "/v1beta/models/" + MODEL + ":generateContent", Map.of(), null)).isEmpty();
    }

    @Test
    void parsesGeminiRequestBody() throws Exception {
        ChatRequestView v = d.parseChat(CHAT, m.readTree("""
                {"systemInstruction":{"parts":[{"text":"be nice"}]},
                 "contents":[{"role":"user","parts":[{"text":"hi"}]}],
                 "generationConfig":{"temperature":0.3}}
                """));
        assertThat(v.modelName()).isEqualTo(MODEL);
        assertThat(v.temperature()).isEqualTo(0.3);
        assertThat(v.messages()).extracting(ChatRequestView.Message::role).containsExactly("system", "user");
    }

    @Test
    void rendersGeminiResponse() throws Exception {
        CapturedRequest r = req(BASE + MODEL + ":generateContent", "{}");
        JsonNode j = m.readTree(d.render(CHAT, ChatReply.text("hello").usage(3, 4), r).body());
        assertThat(j.at("/candidates/0/content/parts/0/text").asText()).isEqualTo("hello");
        assertThat(j.at("/candidates/0/finishReason").asText()).isEqualTo("STOP");
        assertThat(j.at("/usageMetadata/totalTokenCount").asInt()).isEqualTo(7);
        assertThat(j.at("/modelVersion").asText()).isEqualTo(MODEL);
    }

    @Test
    void neverRendersAFunctionCallIdBecauseVertexHasNone() throws Exception {
        ChatReply reply = new ChatReplyImpl(null, List.of(ToolCall.of("c9", "f", "{\"a\":1}")), null, null, null, null,
                null, null, null);
        CapturedRequest r = req(BASE + MODEL + ":generateContent", "{}");
        JsonNode call = m.readTree(d.render(CHAT, reply, r).body()).at("/candidates/0/content/parts/0/functionCall");
        assertThat(call.path("name").asText()).isEqualTo("f");
        assertThat(call.has("id")).isFalse();

        List<SseEvent> events = d.render(STREAM, reply, r).events();
        JsonNode streamed = m.readTree(events.get(events.size() - 1).data())
                .at("/candidates/0/content/parts/0/functionCall");
        assertThat(streamed.path("name").asText()).isEqualTo("f");
        assertThat(streamed.has("id")).isFalse();
    }

    @Test
    void rendersPredictEmbeddingsInInstanceOrder() throws Exception {
        CapturedRequest r = req(BASE + "text-embedding-004:predict", """
                {"instances":[{"content":"a b"},{"content":"c","task_type":"RETRIEVAL_QUERY"}],
                 "parameters":{"autoTruncate":true}}
                """);
        JsonNode j = m.readTree(d.render(EMBED, EmbeddingReply.of(new float[] { 1, 0 }, new float[] { 0, 1 }), r).body());
        assertThat(j.at("/predictions")).hasSize(2);
        assertThat(j.at("/predictions/0/embeddings/values/0").floatValue()).isEqualTo(1f);
        assertThat(j.at("/predictions/1/embeddings/values/1").floatValue()).isEqualTo(1f);
        assertThat(j.at("/predictions/0/embeddings/statistics/truncated").asBoolean()).isFalse();
        assertThat(j.at("/predictions/0/embeddings/statistics/token_count").asInt()).isEqualTo(2);
        assertThat(j.at("/predictions/1/embeddings/statistics/token_count").asInt()).isEqualTo(1);

        JsonNode derived = m.readTree(d.render(EMBED, EmbeddingReply.derived(3), r).body());
        assertThat(derived.at("/predictions")).hasSize(2);
        assertThat(derived.at("/predictions/0/embeddings/values")).hasSize(3);
    }

    @Test
    void tooFewEmbeddingVectorsIsAnError() {
        CapturedRequest r = req(BASE + "text-embedding-004:predict",
                "{\"instances\":[{\"content\":\"a\"},{\"content\":\"b\"}]}");
        assertThatThrownBy(() -> d.render(EMBED, EmbeddingReply.of(new float[] { 1 }), r))
                .isInstanceOf(UnrenderableReply.class).hasMessageContaining("2 inputs");
    }

    @Test
    void rendersGoogleErrorEnvelope() throws Exception {
        Rendered r = d.renderError(CHAT, 400, "bad");
        assertThat(r.status()).isEqualTo(400);
        JsonNode j = m.readTree(r.body());
        assertThat(j.at("/error/code").asInt()).isEqualTo(400);
        assertThat(j.at("/error/message").asText()).isEqualTo("bad");
        assertThat(j.at("/error/status").asText()).isEqualTo("INVALID_ARGUMENT");
    }
}
