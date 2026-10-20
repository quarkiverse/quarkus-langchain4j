package io.quarkiverse.langchain4j.mockapi.internal.dialect.vertexai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.ChatRequestView;
import io.quarkiverse.langchain4j.mockapi.Operation;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Dialect;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Rendered;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.UnrenderableReply;
import io.quarkiverse.langchain4j.mockapi.internal.json.MockApiJson;
import io.quarkiverse.langchain4j.mockapi.internal.model.CapturedRequestImpl;

class VertexAiDialectTest {

    private static final String MODEL = "chat-bison";
    private static final String PATH = "/v1/projects/mock-project/locations/us-central1/publishers/google/models/"
            + MODEL + ":predict";
    private static final Dialect.Match CHAT = new Dialect.Match(Operation.CHAT, false, MODEL);

    VertexAiDialect d = new VertexAiDialect();
    ObjectMapper m = MockApiJson.mapper();

    static CapturedRequest req() {
        return new CapturedRequestImpl(1, "POST", "/vertexai" + PATH, null, Map.of(), "{}", Operation.CHAT,
                "vertexai", null, null);
    }

    @Test
    void hasItsOwnIdAndPrefix() {
        assertThat(d.id()).isEqualTo("vertexai");
        assertThat(d.pathPrefix()).isEqualTo("/vertexai");
    }

    @Test
    void matchesPredict() {
        assertThat(d.match("POST", PATH, Map.of(), null)).contains(CHAT);
        assertThat(d.match("POST", "/v1/projects/p/locations/l/publishers/acme/models/m:predict", Map.of(), null))
                .contains(new Dialect.Match(Operation.CHAT, false, "m"));
        assertThat(d.match("GET", PATH, Map.of(), null)).isEmpty();
        assertThat(d.match("POST", PATH.replace(":predict", ":generateContent"), Map.of(), null)).isEmpty();
        assertThat(d.match("POST", "/v1/chat/completions", Map.of(), null)).isEmpty();
    }

    @Test
    void parsesChatInstance() throws Exception {
        ChatRequestView v = d.parseChat(CHAT, m.readTree("""
                {"instances":[{"context":"be nice",
                               "messages":[{"author":"USER","content":"hi"},{"author":"AI","content":"hello"},
                                           {"author":"USER","content":"how are you?"}]}],
                 "parameters":{"temperature":0.2,"maxOutputTokens":50,"topK":40,"topP":0.9}}
                """));
        assertThat(v.modelName()).isEqualTo(MODEL);
        assertThat(v.stream()).isFalse();
        assertThat(v.temperature()).isEqualTo(0.2);
        assertThat(v.topP()).isEqualTo(0.9);
        assertThat(v.maxTokens()).isEqualTo(50);
        assertThat(v.tools()).isEmpty();
        assertThat(v.messages()).extracting(ChatRequestView.Message::role)
                .containsExactly("system", "user", "assistant", "user");
        assertThat(v.messages()).extracting(ChatRequestView.Message::text)
                .containsExactly("be nice", "hi", "hello", "how are you?");
    }

    @Test
    void parsesMinimalInstance() throws Exception {
        ChatRequestView v = d.parseChat(CHAT, m.readTree("""
                {"instances":[{"context":"","messages":[{"author":"USER","content":"hi"}]}],
                 "parameters":{"temperature":null,"maxOutputTokens":null}}
                """));
        assertThat(v.temperature()).isNull();
        assertThat(v.maxTokens()).isNull();
        assertThat(v.messages()).singleElement().satisfies(msg -> {
            assertThat(msg.role()).isEqualTo("user");
            assertThat(msg.text()).isEqualTo("hi");
        });
    }

    @Test
    void rendersPrediction() throws Exception {
        Rendered r = d.render(CHAT, ChatReply.text("Nice to meet you").usage(11, 37), req());
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.headers()).containsEntry("Content-Type", "application/json");
        JsonNode j = m.readTree(r.body());
        assertThat(j.at("/predictions")).hasSize(1);
        assertThat(j.at("/predictions/0/candidates")).hasSize(1);
        assertThat(j.at("/predictions/0/candidates/0/content").asText()).isEqualTo("Nice to meet you");
        assertThat(j.at("/predictions/0/candidates/0/author").asText()).isEqualTo("1");
        assertThat(j.at("/metadata/tokenMetadata/inputTokenCount/totalTokens").asInt()).isEqualTo(11);
        assertThat(j.at("/metadata/tokenMetadata/outputTokenCount/totalTokens").asInt()).isEqualTo(37);
    }

    @Test
    void rendersEmptyContentAndZeroUsageByDefault() throws Exception {
        JsonNode j = m.readTree(d.render(CHAT, ChatReply.text(null), req()).body());
        assertThat(j.at("/predictions/0/candidates/0/content").asText()).isEmpty();
        assertThat(j.at("/metadata/tokenMetadata/inputTokenCount/totalTokens").asInt()).isZero();
    }

    @Test
    void refusesToolCallsBecausePalmChatHasNone() {
        assertThatThrownBy(() -> d.render(CHAT, ChatReply.toolCall("f", "{}"), req()))
                .isInstanceOf(UnrenderableReply.class).hasMessageContaining("no tool calls");
    }

    @Test
    void rendersGoogleErrorEnvelope() throws Exception {
        Rendered r = d.renderError(CHAT, 404, "no such model");
        assertThat(r.status()).isEqualTo(404);
        JsonNode j = m.readTree(r.body());
        assertThat(j.at("/error/code").asInt()).isEqualTo(404);
        assertThat(j.at("/error/message").asText()).isEqualTo("no such model");
        assertThat(j.at("/error/status").asText()).isEqualTo("NOT_FOUND");
    }
}
