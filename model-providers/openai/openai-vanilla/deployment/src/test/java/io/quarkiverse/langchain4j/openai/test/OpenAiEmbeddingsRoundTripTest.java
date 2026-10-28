package io.quarkiverse.langchain4j.openai.test;

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
import io.quarkiverse.langchain4j.mockapi.EmbeddingReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class OpenAiEmbeddingsRoundTripTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.embedding-model.provider", "openai");

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void derivedDefaultIsDeterministic() {
        float[] first = embeddingModel.embed("a").content().vector();
        float[] second = embeddingModel.embed("a").content().vector();

        assertThat(first).hasSize(4);
        assertThat(second).isEqualTo(first);
    }

    @Test
    void explicitVector() {
        mock.embeddings().reply(EmbeddingReply.of(new float[] { 1, 0, 0, 0 }));

        assertThat(embeddingModel.embed("a").content().vector()).containsExactly(1, 0, 0, 0);
    }

    @Test
    void embedAllReturnsOneVectorPerInput() {
        mock.embeddings().reply(EmbeddingReply.of(new float[] { 1, 0, 0, 0 }, new float[] { 0, 1, 0, 0 }));

        List<Embedding> embeddings = embeddingModel.embedAll(List.of(TextSegment.from("a"), TextSegment.from("b")))
                .content();

        assertThat(embeddings).hasSize(2);
        assertThat(embeddings.get(0).vector()).containsExactly(1, 0, 0, 0);
        assertThat(embeddings.get(1).vector()).containsExactly(0, 1, 0, 0);
        assertThat(mock.embeddings().singleRequest().bodyAsJson().at("/input")).hasSize(2);
    }
}
