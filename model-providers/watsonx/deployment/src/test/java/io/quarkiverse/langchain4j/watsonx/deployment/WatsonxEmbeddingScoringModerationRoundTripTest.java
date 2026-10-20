package io.quarkiverse.langchain4j.watsonx.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.EmbeddingReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkiverse.langchain4j.mockapi.ModerationReply;
import io.quarkiverse.langchain4j.mockapi.ScoringReply;
import io.quarkus.test.QuarkusExtensionTest;

public class WatsonxEmbeddingScoringModerationRoundTripTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.embedding-model.provider", "watsonx")
            .overrideConfigKey("quarkus.langchain4j.scoring-model.provider", "watsonx")
            .overrideConfigKey("quarkus.langchain4j.moderation-model.provider", "watsonx")
            // the moderation model needs at least one detector
            .overrideConfigKey("quarkus.langchain4j.watsonx.moderation-model.hap.enabled", "true");

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    ScoringModel scoringModel;

    @Inject
    ModerationModel moderationModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void embedAllGivesOneVectorPerSegment() {
        mock.embeddings().reply(EmbeddingReply.of(new float[] { 0.1f, 0.2f }, new float[] { 0.3f, 0.4f }));

        Response<List<Embedding>> response = embeddingModel.embedAll(List.of(TextSegment.from("a"), TextSegment.from("b")));

        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(1).vector()).containsExactly(0.3f, 0.4f);
        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(2);
        CapturedRequest r = mock.embeddings().singleRequest();
        assertThat(r.path()).isEqualTo("/watsonx/ml/v1/text/embeddings");
        assertThat(r.bodyAsJson().at("/model_id").asText()).isEqualTo("ibm/granite-embedding-278m-multilingual");
        assertThat(r.bodyAsJson().at("/inputs")).hasSize(2);
    }

    @Test
    void scoreAllKeepsSegmentOrder() {
        mock.scoring().reply(ScoringReply.of(0.2, 0.9, 0.5));

        Response<List<Double>> response = scoringModel.scoreAll(
                List.of(TextSegment.from("a"), TextSegment.from("b"), TextSegment.from("c")), "q");

        assertThat(response.content()).containsExactly(0.2, 0.9, 0.5);
        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(3);
        CapturedRequest r = mock.scoring().singleRequest();
        assertThat(r.path()).isEqualTo("/watsonx/ml/v1/text/rerank");
        assertThat(r.bodyAsJson().at("/query").asText()).isEqualTo("q");
        assertThat(r.bodyAsJson().at("/inputs/1/text").asText()).isEqualTo("b");
    }

    @Test
    void moderationFlagsADetection() {
        mock.moderation().reply(ModerationReply.flagged("hap"));

        Moderation moderation = moderationModel.moderate("you idiot").content();

        assertThat(moderation.flagged()).isTrue();
        // DIVERGENCE: the client takes the flagged text from a "text" field, which the documented detection items do
        // not have (they carry the detected string in "detection"), so there is no flagged text
        assertThat(moderation.flaggedText()).isNull();
        CapturedRequest r = mock.moderation().singleRequest();
        assertThat(r.path()).isEqualTo("/watsonx/ml/v1/text/detection");
        assertThat(r.bodyAsJson().at("/input").asText()).isEqualTo("you idiot");
        assertThat(r.bodyAsJson().at("/detectors").has("hap")).isTrue();
    }

    @Test
    void notFlaggedByDefault() {
        assertThat(moderationModel.moderate("hello").content().flagged()).isFalse();
    }
}
