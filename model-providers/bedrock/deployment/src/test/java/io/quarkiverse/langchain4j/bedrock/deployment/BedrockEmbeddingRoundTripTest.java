package io.quarkiverse.langchain4j.bedrock.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.EmbeddingReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * The default embedding model is Cohere Embed (one request for all inputs); the named {@code titan} model is Titan Text
 * Embeddings V2 (one request per input).
 */
public class BedrockEmbeddingRoundTripTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset("""
                            quarkus.langchain4j.bedrock.embedding-model.cohere.input-type=search_document
                            quarkus.langchain4j.bedrock.titan.embedding-model.model-id=amazon.titan-embed-text-v2:0
                            """), "application.properties"))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.embedding-model.provider", "bedrock")
            .overrideConfigKey("quarkus.langchain4j.titan.embedding-model.provider", "bedrock");

    @Inject
    EmbeddingModel cohere;

    @Inject
    @ModelName("titan")
    EmbeddingModel titan;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void cohereEmbedsAllInputsInOneRequest() {
        mock.embeddings().reply(EmbeddingReply.of(new float[] { 0.1f, 0.2f }, new float[] { 0.3f, 0.4f }));

        List<Embedding> embeddings = cohere.embedAll(List.of(TextSegment.from("one"), TextSegment.from("two"))).content();

        assertThat(embeddings).extracting(Embedding::vector)
                .containsExactly(new float[] { 0.1f, 0.2f }, new float[] { 0.3f, 0.4f });
        CapturedRequest r = mock.embeddings().singleRequest();
        assertThat(r.path()).isEqualTo("/bedrock/model/cohere.embed-english-v3/invoke");
        assertThat(r.dialect()).isEqualTo("bedrock");
        assertThat(r.bodyAsJson().at("/texts/1").asText()).isEqualTo("two");
        assertThat(r.bodyAsJson().at("/input_type").asText()).isEqualTo("search_document");
    }

    @Test
    void titanEmbedsOneInputPerRequest() {
        mock.embeddings()
                .replies(List.of(EmbeddingReply.of(new float[] { 1f, 0f }), EmbeddingReply.of(new float[] { 0f, 1f })));

        List<Embedding> embeddings = titan.embedAll(List.of(TextSegment.from("one"), TextSegment.from("two words")))
                .content();

        assertThat(embeddings).extracting(Embedding::vector).containsExactly(new float[] { 1f, 0f }, new float[] { 0f, 1f });
        List<CapturedRequest> requests = mock.embeddings().requests();
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).path()).isEqualTo("/bedrock/model/amazon.titan-embed-text-v2%3A0/invoke");
        assertThat(requests.get(1).bodyAsJson().at("/inputText").asText()).isEqualTo("two words");
    }
}
