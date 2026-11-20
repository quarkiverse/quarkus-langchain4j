package io.quarkiverse.langchain4j.ollama.deployment;

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
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.EmbeddingReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class OllamaEmbeddingRoundTripTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.embedding-model.provider", "ollama");

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void embedAllSendsOneRequestPerInput() {
        mock.embeddings().reply(EmbeddingReply.of(new float[] { 1, 0, 0, 0 }), EmbeddingReply.of(new float[] { 0, 1, 0, 0 }));

        List<Embedding> embeddings = embeddingModel.embedAll(List.of(TextSegment.from("a"), TextSegment.from("b")))
                .content();

        assertThat(embeddings).hasSize(2);
        assertThat(embeddings.get(0).vector()).containsExactly(1, 0, 0, 0);
        assertThat(embeddings.get(1).vector()).containsExactly(0, 1, 0, 0);
        List<CapturedRequest> requests = mock.embeddings().requests();
        assertThat(requests).hasSize(2);
        assertThat(requests).allSatisfy(r -> {
            assertThat(r.path()).isEqualTo("/ollama/api/embed");
            assertThat(r.dialect()).isEqualTo("ollama");
            assertThat(r.bodyAsJson().at("/model").asText()).isEqualTo("nomic-embed-text");
        });
        assertThat(requests).extracting(r -> r.bodyAsJson().at("/input").asText()).containsExactly("a", "b");
    }

    @Test
    void derivedDefaultIsDeterministic() {
        float[] first = embeddingModel.embed("a").content().vector();

        assertThat(first).hasSize(4);
        assertThat(embeddingModel.embed("a").content().vector()).isEqualTo(first);
    }
}
