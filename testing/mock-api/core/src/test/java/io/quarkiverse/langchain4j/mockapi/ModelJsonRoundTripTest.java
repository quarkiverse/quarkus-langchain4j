package io.quarkiverse.langchain4j.mockapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.langchain4j.mockapi.internal.json.MockApiJson;
import io.quarkiverse.langchain4j.mockapi.internal.model.CapturedRequestImpl;
import io.quarkiverse.langchain4j.mockapi.internal.model.RawStub;

class ModelJsonRoundTripTest {

    private static final ObjectMapper MAPPER = MockApiJson.mapper();

    private static Reply roundTrip(Reply r) throws Exception {
        return MAPPER.readValue(MAPPER.writeValueAsString(r), Reply.class);
    }

    @Test
    void decisionReplyRoundTrips() throws Exception {
        DecisionReply r = DecisionReply.of(DecisionReply.predicate("p", 0.9),
                DecisionReply.choice("c", "a", Map.of("a", 1.0), 0.5),
                DecisionReply.score("s", 1.0, List.of(DecisionReply.level(0, "Low", 1.0)), null),
                DecisionReply.declined("d")).usage(3, 4).modelName("m");
        assertThat(roundTrip(r)).isEqualTo(r);
        assertThat(MAPPER.writeValueAsString(r)).contains("\"kind\":\"DecisionReply\"").contains("\"type\":\"declined\"");
    }

    @Test
    void documentedAdminBodyDeserializes() throws Exception {
        // the exact body the guide's curl example sends to the admin API
        Reply r = MAPPER.readValue("{\"kind\":\"ChatReply\",\"text\":\"hi\"}", Reply.class);
        assertThat(r).isInstanceOf(ChatReply.class);
        assertThat(((ChatReply) r).text()).isEqualTo("hi");
        assertThat(((ChatReply) r).finishReason()).isEqualTo(FinishReason.STOP);
    }

    @Test
    void chatReplyRoundTrips() throws Exception {
        ChatReply r = ChatReply.text("hi").addToolCall("getWeather", Map.of("city", "Paris")).usage(1, 2)
                .delay(Duration.ofMillis(5)).chunks(List.of("h", "i"));
        Reply back = roundTrip(r);
        assertThat(back).isInstanceOf(ChatReply.class);
        ChatReply c = (ChatReply) back;
        assertThat(c.text()).isEqualTo("hi");
        assertThat(c.toolCalls()).singleElement().satisfies(t -> {
            assertThat(t.name()).isEqualTo("getWeather");
            assertThat(t.argumentsJson()).isEqualTo("{\"city\":\"Paris\"}");
        });
        assertThat(c.finishReason()).isEqualTo(FinishReason.TOOL_CALLS);
        assertThat(c.usage()).isEqualTo(Usage.of(1, 2));
        assertThat(c.delay()).isEqualTo(Duration.ofMillis(5));
        assertThat(c.chunks()).containsExactly("h", "i");
    }

    @Test
    void everyReplyKindRoundTrips() throws Exception {
        ErrorReply error = ErrorReply.of(429, "slow");
        assertThat(roundTrip(error)).isEqualTo(error);

        Reply embedding = roundTrip(EmbeddingReply.of(new float[] { 0.1f }));
        assertThat(embedding).isInstanceOf(EmbeddingReply.class);
        assertThat(((EmbeddingReply) embedding).vectors()).hasSize(1);
        assertThat(Arrays.equals(((EmbeddingReply) embedding).vectors().get(0), new float[] { 0.1f })).isTrue();

        ModerationReply moderation = ModerationReply.flagged("hate");
        assertThat(roundTrip(moderation)).isEqualTo(moderation);

        ScoringReply scoring = ScoringReply.of(0.5);
        assertThat(roundTrip(scoring)).isEqualTo(scoring);

        RawResponse raw = RawResponse.of(202, Map.of("X", "y"), "{}");
        assertThat(roundTrip(raw)).isEqualTo(raw);

        Reply derived = roundTrip(EmbeddingReply.derived(8));
        assertThat(((EmbeddingReply) derived).vectors()).isNull();
        assertThat(((EmbeddingReply) derived).derivedDimension()).isEqualTo(8);
    }

    @Test
    void rawStubWritesTheDocumentedKind() throws Exception {
        RawStub stub = new RawStub("POST", "/x/**", null, null,
                List.of(RawResponse.json(202, "{}"), RawResponse.json(200, "{}")));
        String json = MockApiJson.mapper().writeValueAsString(stub);
        assertThat(json).contains("\"kind\":\"RawResponse\"").doesNotContain("Impl");
        RawStub back = MockApiJson.mapper().readValue(json, RawStub.class);
        assertThat(back.responses()).extracting(RawResponse::status).containsExactly(202, 200);
    }

    @Test
    void sseRawResponseRoundTrips() throws Exception {
        RawResponse sse = RawResponse.sse(200, Map.of("X", "y"), List.of(SseEvent.of("e", "d")));
        assertThat(roundTrip(sse)).isEqualTo(sse);
        assertThat(sse.events()).containsExactly(SseEvent.of("e", "d"));
    }

    @Test
    void capturedRequestHelpers() {
        CapturedRequest r = new CapturedRequestImpl(1, "POST", "/v1/chat/completions", null,
                Map.of("Authorization", List.of("Bearer k")), "{\"model\":\"gpt\"}", Operation.CHAT, "openai", null,
                null);
        assertThat(r.header("authorization")).isEqualTo("Bearer k");
        assertThat(r.bodyAsJson().get("model").asText()).isEqualTo("gpt");
        assertThatThrownBy(r::asChat).isInstanceOf(IllegalStateException.class).hasMessageContaining("/v1/chat/completions");
    }

    @Test
    void journalMustBeAnArray() {
        assertThatThrownBy(() -> MockModelServer.parseRequests(MockApiJson.mapper().missingNode()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not a JSON array");
        assertThatThrownBy(() -> MockModelServer.parseRequests(MockApiJson.mapper().nullNode()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not a JSON array");
        assertThat(MockModelServer.parseRequests(MockApiJson.mapper().createArrayNode())).isEmpty();
    }
}
