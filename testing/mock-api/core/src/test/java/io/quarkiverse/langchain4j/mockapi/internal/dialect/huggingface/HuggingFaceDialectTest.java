package io.quarkiverse.langchain4j.mockapi.internal.dialect.huggingface;

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
import io.quarkiverse.langchain4j.mockapi.Operation;
import io.quarkiverse.langchain4j.mockapi.ScoringReply;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Dialect;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.DialectRegistry;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Rendered;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.UnrenderableReply;
import io.quarkiverse.langchain4j.mockapi.internal.json.MockApiJson;
import io.quarkiverse.langchain4j.mockapi.internal.model.CapturedRequestImpl;

class HuggingFaceDialectTest {

    private static final Dialect.Match CHAT = new Dialect.Match(Operation.CHAT, false, "text-generation");
    private static final Dialect.Match EMBEDDINGS = new Dialect.Match(Operation.EMBEDDINGS, false, "feature-extraction");

    HuggingFaceDialect d = new HuggingFaceDialect();
    ObjectMapper m = MockApiJson.mapper();

    static CapturedRequest req(String body) {
        return new CapturedRequestImpl(1, "POST", "/huggingface/chat", null, Map.of(), body, Operation.CHAT,
                "huggingface", null, null);
    }

    @Test
    void matchesItsOperationsUnderItsPrefix() {
        assertThat(d.id()).isEqualTo("huggingface");
        assertThat(d.pathPrefix()).isEqualTo("/huggingface");
        assertThat(d.match("POST", "/chat", Map.of(), null)).contains(CHAT);
        assertThat(d.match("POST", "/embeddings", Map.of(), null)).contains(EMBEDDINGS);
        assertThat(d.match("GET", "/chat", Map.of(), null)).isEmpty();
        assertThat(d.match("POST", "/generate", Map.of(), null)).isEmpty();
    }

    @Test
    void huggingFaceRequestsAreResolvedByPrefix() {
        DialectRegistry registry = DialectRegistry.load();
        assertThat(registry.resolve("POST", "/huggingface/chat", Map.of(), null))
                .get().extracting(r -> r.dialect().id()).isEqualTo("huggingface");
        assertThat(registry.resolve("POST", "/huggingface/embeddings", Map.of(), null))
                .get().extracting(r -> r.dialect().id()).isEqualTo("huggingface");
    }

    @Test
    void parsesTextGenerationRequest() throws Exception {
        ChatRequestView v = d.parseChat(CHAT, m.readTree("""
                {"inputs":"be nice\\nhi","parameters":{"temperature":0.2,"top_p":0.7,"max_new_tokens":100,
                 "return_full_text":false},"options":{"wait_for_model":true}}
                """));
        assertThat(v.modelName()).isNull();
        assertThat(v.stream()).isFalse();
        assertThat(v.temperature()).isEqualTo(0.2);
        assertThat(v.topP()).isEqualTo(0.7);
        assertThat(v.maxTokens()).isEqualTo(100);
        assertThat(v.tools()).isEmpty();
        assertThat(v.messages()).singleElement().satisfies(msg -> {
            assertThat(msg.role()).isEqualTo("user");
            assertThat(msg.text()).isEqualTo("be nice\nhi");
        });
    }

    @Test
    void rendersGeneratedTextInAList() throws Exception {
        Rendered r = d.render(CHAT, ChatReply.text("hello"), req("{\"inputs\":\"hi\"}"));

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.headers()).containsEntry("Content-Type", "application/json");
        JsonNode j = m.readTree(r.body());
        assertThat(j.isArray()).isTrue();
        assertThat(j).hasSize(1);
        assertThat(j.at("/0/generated_text").asText()).isEqualTo("hello");
        assertThat(j.at("/0").size()).isEqualTo(1);
    }

    @Test
    void prependsThePromptWhenFullTextIsRequested() throws Exception {
        Rendered r = d.render(CHAT, ChatReply.text(" there"),
                req("{\"inputs\":\"hi\",\"parameters\":{\"return_full_text\":true}}"));

        assertThat(m.readTree(r.body()).at("/0/generated_text").asText()).isEqualTo("hi there");
    }

    @Test
    void toolCallsAreAnError() {
        assertThatThrownBy(
                () -> d.render(CHAT, ChatReply.toolCall("getWeather", Map.of("city", "Paris")), req("{\"inputs\":\"hi\"}")))
                .isInstanceOf(UnrenderableReply.class).hasMessageContaining("tool calls");
    }

    @Test
    void rendersOneVectorPerInput() throws Exception {
        Rendered r = d.render(EMBEDDINGS, EmbeddingReply.of(new float[] { 1, 0 }, new float[] { 0, 1 }),
                req("{\"inputs\":[\"a\",\"b\"],\"options\":{\"wait_for_model\":true}}"));

        JsonNode j = m.readTree(r.body());
        assertThat(j).hasSize(2);
        assertThat(j.at("/0/0").floatValue()).isEqualTo(1f);
        assertThat(j.at("/0/1").floatValue()).isEqualTo(0f);
        assertThat(j.at("/1/1").floatValue()).isEqualTo(1f);
    }

    @Test
    void aSingleStringInputGetsOneDerivedVector() throws Exception {
        JsonNode j = m.readTree(d.render(EMBEDDINGS, EmbeddingReply.derived(3), req("{\"inputs\":\"a\"}")).body());

        assertThat(j).hasSize(1);
        assertThat(j.get(0)).hasSize(3);
    }

    @Test
    void tooFewVectorsAreAnError() {
        assertThatThrownBy(
                () -> d.render(EMBEDDINGS, EmbeddingReply.of(new float[] { 1 }), req("{\"inputs\":[\"a\",\"b\"]}")))
                .isInstanceOf(UnrenderableReply.class).hasMessageContaining("2 inputs");
    }

    @Test
    void errorsCarryTheDocumentedErrorType() throws Exception {
        JsonNode chat = m.readTree(d.renderError(CHAT, 429, "busy").body());
        assertThat(chat.at("/error").asText()).isEqualTo("busy");
        assertThat(chat.at("/error_type").asText()).isEqualTo("overloaded");
        assertThat(m.readTree(d.renderError(CHAT, 422, "x").body()).at("/error_type").asText()).isEqualTo("validation");
        assertThat(m.readTree(d.renderError(CHAT, 503, "x").body()).at("/error_type").asText()).isEqualTo("generation");

        Rendered embeddings = d.renderError(EMBEDDINGS, 413, "too big");
        assertThat(embeddings.status()).isEqualTo(413);
        assertThat(m.readTree(embeddings.body()).at("/error_type").asText()).isEqualTo("validation");
        assertThat(m.readTree(d.renderError(EMBEDDINGS, 500, "x").body()).at("/error_type").asText()).isEqualTo("backend");
    }

    @Test
    void unsupportedReplyIsUnrenderable() {
        assertThatThrownBy(() -> d.render(CHAT, ScoringReply.equalScores(), req("{\"inputs\":\"hi\"}")))
                .isInstanceOf(UnrenderableReply.class).hasMessageContaining("not supported");
    }
}
