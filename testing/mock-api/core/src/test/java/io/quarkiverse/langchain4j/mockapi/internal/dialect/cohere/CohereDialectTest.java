package io.quarkiverse.langchain4j.mockapi.internal.dialect.cohere;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.EmbeddingReply;
import io.quarkiverse.langchain4j.mockapi.Operation;
import io.quarkiverse.langchain4j.mockapi.ScoringReply;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Dialect;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.DialectRegistry;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Rendered;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.UnrenderableReply;
import io.quarkiverse.langchain4j.mockapi.internal.json.MockApiJson;
import io.quarkiverse.langchain4j.mockapi.internal.model.CapturedRequestImpl;

class CohereDialectTest {

    private static final Dialect.Match RERANK = new Dialect.Match(Operation.SCORING, false, "rerank");

    CohereDialect d = new CohereDialect();
    ObjectMapper m = MockApiJson.mapper();

    static CapturedRequest req(String body) {
        return new CapturedRequestImpl(1, "POST", "/cohere/v2/rerank", null, Map.of(), body, Operation.SCORING,
                "cohere", null, null);
    }

    @Test
    void matchesRerankUnderItsPrefix() {
        assertThat(d.id()).isEqualTo("cohere");
        assertThat(d.pathPrefix()).isEqualTo("/cohere");
        assertThat(d.match("POST", "/v2/rerank", Map.of(), null)).contains(RERANK);
        assertThat(d.match("GET", "/v2/rerank", Map.of(), null)).isEmpty();
        assertThat(d.match("POST", "/v1/rerank", Map.of(), null)).isEmpty();
        assertThat(d.match("POST", "/v2/chat", Map.of(), null)).isEmpty();
    }

    @Test
    void cohereRequestsAreResolvedByPrefix() {
        assertThat(DialectRegistry.load().resolve("POST", "/cohere/v2/rerank", Map.of(), null))
                .get().extracting(r -> r.dialect().id()).isEqualTo("cohere");
    }

    @Test
    void rendersScoresMostRelevantFirstWithTheirDocumentIndex() throws Exception {
        Rendered r = d.render(RERANK, ScoringReply.of(0.2, 0.9, 0.5),
                req("{\"model\":\"rerank-v3.5\",\"query\":\"q\",\"documents\":[\"a\",\"b\",\"c\"]}"));

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.headers()).containsEntry("Content-Type", "application/json");
        JsonNode j = m.readTree(r.body());
        assertThat(j.at("/id").asText()).isNotBlank();
        assertThat(j.at("/results")).hasSize(3);
        assertThat(j.at("/results/0/index").asInt()).isEqualTo(1);
        assertThat(j.at("/results/0/relevance_score").asDouble()).isEqualTo(0.9);
        assertThat(j.at("/results/1/index").asInt()).isEqualTo(2);
        assertThat(j.at("/results/1/relevance_score").asDouble()).isEqualTo(0.5);
        assertThat(j.at("/results/2/index").asInt()).isEqualTo(0);
        assertThat(j.at("/results/2/relevance_score").asDouble()).isEqualTo(0.2);
        assertThat(j.at("/results/0").size()).isEqualTo(2);
        assertThat(j.at("/meta/api_version/version").asText()).isEqualTo("2");
        assertThat(j.at("/meta/billed_units/search_units").asInt()).isEqualTo(1);
    }

    @Test
    void honoursTopN() throws Exception {
        Rendered r = d.render(RERANK, ScoringReply.of(0.2, 0.9, 0.5),
                req("{\"query\":\"q\",\"documents\":[\"a\",\"b\",\"c\"],\"top_n\":2}"));

        JsonNode j = m.readTree(r.body());
        assertThat(j.at("/results")).hasSize(2);
        assertThat(j.at("/results/0/index").asInt()).isEqualTo(1);
        assertThat(j.at("/results/1/index").asInt()).isEqualTo(2);
    }

    @Test
    void equalScoresGiveEveryDocumentTheSameScoreInDocumentOrder() throws Exception {
        Rendered r = d.render(RERANK, ScoringReply.equalScores(),
                req("{\"query\":\"q\",\"documents\":[\"a\",{\"text\":\"b\"}]}"));

        JsonNode j = m.readTree(r.body());
        assertThat(j.at("/results")).hasSize(2);
        assertThat(j.at("/results/0/index").asInt()).isEqualTo(0);
        assertThat(j.at("/results/1/index").asInt()).isEqualTo(1);
        assertThat(j.at("/results/0/relevance_score").asDouble()).isEqualTo(j.at("/results/1/relevance_score").asDouble());
    }

    @Test
    void extraScoresAreIgnoredAndTooFewAreAnError() throws Exception {
        JsonNode j = m.readTree(d.render(RERANK, ScoringReply.of(0.1, 0.2, 0.3),
                req("{\"query\":\"q\",\"documents\":[\"a\"]}")).body());
        assertThat(j.at("/results")).hasSize(1);

        assertThatThrownBy(
                () -> d.render(RERANK, ScoringReply.of(0.1), req("{\"query\":\"q\",\"documents\":[\"a\",\"b\"]}")))
                .isInstanceOf(UnrenderableReply.class).hasMessageContaining("1 scores").hasMessageContaining("2 documents");
    }

    @Test
    void errorHasAMessage() throws Exception {
        Rendered r = d.renderError(RERANK, 429, "slow down");

        assertThat(r.status()).isEqualTo(429);
        JsonNode j = m.readTree(r.body());
        assertThat(j.at("/message").asText()).isEqualTo("slow down");
        assertThat(j.size()).isEqualTo(1);
    }

    @Test
    void unsupportedReplyIsUnrenderable() {
        assertThatThrownBy(() -> d.render(RERANK, EmbeddingReply.derived(2), req("{}")))
                .isInstanceOf(UnrenderableReply.class).hasMessageContaining("not supported");
    }
}
