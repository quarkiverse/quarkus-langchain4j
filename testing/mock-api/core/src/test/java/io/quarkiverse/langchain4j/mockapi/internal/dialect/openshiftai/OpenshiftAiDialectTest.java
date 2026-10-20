package io.quarkiverse.langchain4j.mockapi.internal.dialect.openshiftai;

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
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Dialect;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Rendered;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.UnrenderableReply;
import io.quarkiverse.langchain4j.mockapi.internal.json.MockApiJson;
import io.quarkiverse.langchain4j.mockapi.internal.model.CapturedRequestImpl;

class OpenshiftAiDialectTest {

    private static final Dialect.Match CHAT = new Dialect.Match(Operation.CHAT, false, "text-generation");

    OpenshiftAiDialect d = new OpenshiftAiDialect();
    ObjectMapper m = MockApiJson.mapper();

    static CapturedRequest req(String body) {
        return new CapturedRequestImpl(3, "POST", "/openshift-ai/api/v1/task/text-generation", null, Map.of(), body,
                Operation.CHAT, "openshift-ai", null, null);
    }

    @Test
    void matchesTextGenerationUnderItsPrefix() {
        assertThat(d.id()).isEqualTo("openshift-ai");
        assertThat(d.pathPrefix()).isEqualTo("/openshift-ai");
        assertThat(d.match("POST", "/api/v1/task/text-generation", Map.of(), null)).contains(CHAT);
        assertThat(d.match("GET", "/api/v1/task/text-generation", Map.of(), null)).isEmpty();
        assertThat(d.match("POST", "/api/v1/task/server-streaming-text-generation", Map.of(), null)).isEmpty();
        assertThat(d.match("POST", "/v1/chat/completions", Map.of(), null)).isEmpty();
    }

    @Test
    void parsesRequest() throws Exception {
        String body = """
                {"model_id":"flan-t5","inputs":"hello there",
                 "parameters":{"max_new_tokens":50,"temperature":0.3,"top_p":0.9}}
                """;
        ChatRequestView v = d.parseChat(CHAT, m.readTree(body));
        assertThat(v.modelName()).isEqualTo("flan-t5");
        assertThat(v.stream()).isFalse();
        assertThat(v.maxTokens()).isEqualTo(50);
        assertThat(v.temperature()).isEqualTo(0.3);
        assertThat(v.topP()).isEqualTo(0.9);
        assertThat(v.tools()).isEmpty();
        assertThat(v.messages()).singleElement().satisfies(msg -> {
            assertThat(msg.role()).isEqualTo("user");
            assertThat(msg.text()).isEqualTo("hello there");
        });
    }

    @Test
    void parsesRequestWithoutParameters() throws Exception {
        ChatRequestView v = d.parseChat(CHAT, m.readTree("{\"model_id\":\"m\",\"inputs\":\"x\"}"));
        assertThat(v.maxTokens()).isNull();
        assertThat(v.temperature()).isNull();
        assertThat(v.topP()).isNull();
    }

    @Test
    void rendersGeneratedTextResult() throws Exception {
        Rendered r = d.render(CHAT, ChatReply.text("74 degrees F").usage(10, 5), req("{\"model_id\":\"m\"}"));
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.headers()).containsEntry("Content-Type", "application/json");
        JsonNode j = m.readTree(r.body());
        assertThat(j.at("/generated_text").asText()).isEqualTo("74 degrees F");
        assertThat(j.at("/generated_tokens").asInt()).isEqualTo(5);
        assertThat(j.at("/input_token_count").asInt()).isEqualTo(10);
        assertThat(j.at("/finish_reason").asText()).isEqualTo("EOS_TOKEN");
        assertThat(j.at("/producer_id/name").isTextual()).isTrue();
        assertThat(j.at("/producer_id/version").isTextual()).isTrue();
        assertThat(j.at("/seed").isNull()).isTrue();
    }

    @Test
    void mapsFinishReasonsAndDefaultsTokenCounts() throws Exception {
        assertThat(field(ChatReply.text("x").finishReason(FinishReason.LENGTH), "/finish_reason")).isEqualTo("MAX_TOKENS");
        assertThat(field(ChatReply.text("x").finishReason(FinishReason.CONTENT_FILTER), "/finish_reason"))
                .isEqualTo("ERROR");
        assertThat(field(ChatReply.text("x"), "/generated_tokens")).isEqualTo("0");
        assertThat(field(ChatReply.text("x"), "/input_token_count")).isEqualTo("0");
    }

    private String field(ChatReply reply, String pointer) throws Exception {
        return m.readTree(d.render(CHAT, reply, req("{}")).body()).at(pointer).asText();
    }

    @Test
    void toolCallsCannotBeRendered() {
        assertThatThrownBy(() -> d.render(CHAT, ChatReply.toolCall("f", "{}"), req("{}")))
                .isInstanceOf(UnrenderableReply.class).hasMessageContaining("cannot return tool calls");
    }

    @Test
    void rendersCaikitErrorEnvelope() throws Exception {
        Rendered r = d.renderError(CHAT, 400, "bad input");
        assertThat(r.status()).isEqualTo(400);
        JsonNode j = m.readTree(r.body());
        assertThat(j.at("/details").asText()).isEqualTo("bad input");
        assertThat(j.at("/code").asInt()).isEqualTo(400);
        assertThat(j.at("/id").isTextual()).isTrue();
    }

    @Test
    void unsupportedReplyIsUnrenderable() {
        assertThatThrownBy(() -> d.render(CHAT, EmbeddingReply.derived(2), req("{}")))
                .isInstanceOf(UnrenderableReply.class).hasMessageContaining("not supported");
    }
}
