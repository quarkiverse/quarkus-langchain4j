package io.quarkiverse.langchain4j.cohere;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkiverse.langchain4j.mockapi.ScoringReply;
import io.quarkus.test.QuarkusExtensionTest;

public class CohereRoundTripTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.scoring-model.provider", "cohere");

    private static final List<TextSegment> SEGMENTS = List.of(TextSegment.from("a"), TextSegment.from("b"),
            TextSegment.from("c"));

    @Inject
    ScoringModel scoringModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void scoresComeBackInSegmentOrderWithPlaceholderApiKey() {
        mock.scoring().reply(ScoringReply.of(0.2, 0.9, 0.5));

        Response<List<Double>> response = scoringModel.scoreAll(SEGMENTS, "q");

        assertThat(response.content()).containsExactly(0.2, 0.9, 0.5);
        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(1);
        CapturedRequest r = mock.scoring().singleRequest();
        assertThat(r.path()).isEqualTo("/cohere/v2/rerank");
        assertThat(r.dialect()).isEqualTo("cohere");
        // no api-key is configured: the extension fills in the placeholder
        assertThat(r.header("Authorization")).isEqualTo("Bearer mock-api-key");
        assertThat(r.bodyAsJson().at("/model").asText()).isEqualTo("rerank-multilingual-v2.0");
        assertThat(r.bodyAsJson().at("/query").asText()).isEqualTo("q");
        assertThat(r.bodyAsJson().at("/documents")).hasSize(3);
        assertThat(r.bodyAsJson().has("top_n")).isFalse();
    }

    @Test
    void equalScoresByDefault() {
        assertThat(scoringModel.scoreAll(SEGMENTS, "q").content()).containsExactly(1.0, 1.0, 1.0);
    }

    @Test
    void errorIsRetriedOnceThenSurfaces() {
        // max-retries defaults to 1, which the client passes to RetryUtils.withRetry as retries, so two attempts
        mock.scoring().fail(500, "boom");
        mock.scoring().fail(500, "boom");

        assertThatThrownBy(() -> scoringModel.scoreAll(SEGMENTS, "q")).hasMessageContaining("500");
        assertThat(mock.scoring().requests()).hasSize(2);
    }
}
