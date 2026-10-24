package io.quarkiverse.langchain4j.mockapi.internal.dialect.openai;

import static io.quarkiverse.langchain4j.mockapi.internal.dialect.openai.OpenAiTestRequests.match;
import static io.quarkiverse.langchain4j.mockapi.internal.dialect.openai.OpenAiTestRequests.req;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.langchain4j.mockapi.EmbeddingReply;
import io.quarkiverse.langchain4j.mockapi.ModerationReply;
import io.quarkiverse.langchain4j.mockapi.Operation;
import io.quarkiverse.langchain4j.mockapi.ScoringReply;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.DerivedEmbeddings;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Dialect;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Rendered;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.UnrenderableReply;
import io.quarkiverse.langchain4j.mockapi.internal.json.MockApiJson;

class OpenAiDialectOtherOpsTest {

    OpenAiDialect d = new OpenAiDialect();
    ObjectMapper m = MockApiJson.mapper();

    @Test
    void embeddingsExplicitVectors() throws Exception {
        float[] v1 = { 0.5f, 0.25f };
        float[] v2 = { 1f, 2f };
        Rendered r = d.render(match(Operation.EMBEDDINGS, false, "embeddings"), EmbeddingReply.of(v1, v2),
                req("/v1/embeddings", Operation.EMBEDDINGS, "{\"input\":[\"a\",\"b\"]}"));
        JsonNode j = m.readTree(r.body());
        assertThat(j.at("/object").asText()).isEqualTo("list");
        assertThat(j.at("/data/0/object").asText()).isEqualTo("embedding");
        assertThat(j.at("/data/0/embedding/0").floatValue()).isEqualTo(0.5f);
        assertThat(j.at("/data/0/embedding/1").floatValue()).isEqualTo(0.25f);
        assertThat(j.at("/data/1/index").asInt()).isEqualTo(1);
        assertThat(j.at("/data/1/embedding/1").floatValue()).isEqualTo(2f);
        assertThat(j.at("/usage/prompt_tokens").asInt()).isEqualTo(2);
        assertThat(j.at("/usage/total_tokens").asInt()).isEqualTo(2);
    }

    @Test
    void embeddingsSingleStringInputDerived() throws Exception {
        Rendered r = d.render(match(Operation.EMBEDDINGS, false, "embeddings"), EmbeddingReply.derived(3),
                req("/v1/embeddings", Operation.EMBEDDINGS, "{\"input\":\"hello\",\"model\":\"e\"}"));
        JsonNode j = m.readTree(r.body());
        assertThat(j.at("/data")).hasSize(1);
        assertThat(j.at("/data/0/embedding")).hasSize(3);
        assertThat(j.at("/data/0/embedding/0").floatValue()).isEqualTo(DerivedEmbeddings.vector("hello", 3)[0]);
        assertThat(j.at("/model").asText()).isEqualTo("e");
    }

    @Test
    void embeddingsTooFewVectorsIsError() {
        assertThatThrownBy(() -> d.render(match(Operation.EMBEDDINGS, false, "embeddings"),
                EmbeddingReply.of(new float[] { 1f }),
                req("/v1/embeddings", Operation.EMBEDDINGS, "{\"input\":[\"a\",\"b\"]}")))
                .isInstanceOf(UnrenderableReply.class).hasMessageContaining("2 inputs");
    }

    @Test
    void embeddingsDerivedAreDeterministic() {
        assertThat(DerivedEmbeddings.vector("hello", 4)).containsExactly(DerivedEmbeddings.vector("hello", 4));
        assertThat(DerivedEmbeddings.vector("hello", 4)).isNotEqualTo(DerivedEmbeddings.vector("world", 4));
        double norm = 0;
        for (float f : DerivedEmbeddings.vector("hello", 8)) {
            norm += f * f;
        }
        assertThat(norm).isCloseTo(1.0, within(1e-5));
    }

    @Test
    void moderationFlagged() throws Exception {
        Rendered r = d.render(new Dialect.Match(Operation.MODERATION, false, "moderations"),
                ModerationReply.flagged("hate"),
                req("/v1/embeddings", Operation.EMBEDDINGS, "{\"input\":\"x\"}"));
        JsonNode j = m.readTree(r.body());
        assertThat(j.at("/id").asText()).isNotEmpty();
        assertThat(j.at("/results/0/flagged").asBoolean()).isTrue();
        assertThat(j.at("/results/0/categories/hate").asBoolean()).isTrue();
        assertThat(j.at("/results/0/categories/violence").asBoolean()).isFalse();
        assertThat(j.at("/results/0/category_scores/hate").asDouble()).isEqualTo(1.0);
        assertThat(j.at("/results/0/category_scores/violence").asDouble()).isZero();
        assertThat(j.at("/results/0/category_applied_input_types/hate/0").asText()).isEqualTo("text");
    }

    @Test
    void unsupportedReplyIsUnrenderable() {
        assertThatThrownBy(() -> d.render(match(Operation.EMBEDDINGS, false, "embeddings"), ScoringReply.equalScores(),
                req("/v1/embeddings", Operation.EMBEDDINGS, "{}")))
                .isInstanceOf(UnrenderableReply.class).hasMessageContaining("not supported");
    }
}
