package io.quarkiverse.langchain4j.mistralai.deployment;

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
import dev.langchain4j.model.moderation.ModerationModel;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.EmbeddingReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkiverse.langchain4j.mockapi.ModerationReply;
import io.quarkus.test.QuarkusExtensionTest;

public class MistralEmbeddingAndModerationRoundTripTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.embedding-model.provider", "mistralai")
            .overrideConfigKey("quarkus.langchain4j.moderation-model.provider", "mistralai");

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    ModerationModel moderationModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void embedAllReturnsOneVectorPerInput() {
        mock.embeddings().reply(EmbeddingReply.of(new float[] { 1, 0, 0, 0 }, new float[] { 0, 1, 0, 0 }));

        List<Embedding> embeddings = embeddingModel.embedAll(List.of(TextSegment.from("a"), TextSegment.from("b")))
                .content();

        assertThat(embeddings).hasSize(2);
        assertThat(embeddings.get(0).vector()).containsExactly(1, 0, 0, 0);
        assertThat(embeddings.get(1).vector()).containsExactly(0, 1, 0, 0);
        CapturedRequest r = mock.embeddings().singleRequest();
        assertThat(r.path()).isEqualTo("/mistralai/v1/embeddings");
        assertThat(r.dialect()).isEqualTo("mistralai");
        assertThat(r.header("Authorization")).isEqualTo("Bearer mock-api-key");
        assertThat(r.bodyAsJson().at("/input")).hasSize(2);
    }

    @Test
    void derivedDefaultIsDeterministic() {
        float[] first = embeddingModel.embed("a").content().vector();

        assertThat(first).hasSize(4);
        assertThat(embeddingModel.embed("a").content().vector()).isEqualTo(first);
    }

    @Test
    void notFlaggedByDefault() {
        assertThat(moderationModel.moderate("x").content().flagged()).isFalse();
        assertThat(mock.moderation().singleRequest().path()).isEqualTo("/mistralai/v1/moderations");
    }

    @Test
    void flaggedReply() {
        mock.moderation().reply(ModerationReply.flagged("violence_and_threats"));

        assertThat(moderationModel.moderate("x").content().flagged()).isTrue();
        assertThat(mock.moderation().singleRequest().dialect()).isEqualTo("mistralai");
    }
}
