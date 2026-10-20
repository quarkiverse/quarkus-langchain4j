package io.quarkiverse.langchain4j.bedrock.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.bedrock.BedrockTitanEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.EmbeddingReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BedrockEmbeddingModelTitanTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(TestCredentialsProvider.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.embedding-model.model-id", "amazon.titan-embed-text-v1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.embedding-model.aws.region", "eu-central-1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.embedding-model.aws.credentials-provider",
                    "TestCredentialsProvider")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.log-requests", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.log-responses", "true")
            .overrideRuntimeConfigKey("quarkus.log.category.\"io.quarkiverse.langchain4j.bedrock\".level", "DEBUG");

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void should_create_bedrock_model() {
        // given

        // when

        // then
        assertThat(ClientProxy.unwrap(embeddingModel)).isInstanceOf(BedrockTitanEmbeddingModel.class);
    }

    @Test
    void should_embed_one() {
        // given
        var segments = List.of(TextSegment.from("one"));
        float[] embedding = generateEmbedding(1536);
        mock.embeddings().reply(EmbeddingReply.of(embedding));

        // when
        var response = embeddingModel.embedAll(segments);

        // then
        assertThat(response).isNotNull();
        assertThat(response.finishReason()).isNull();

        var embeddings = response.content();
        assertThat(embeddings).hasSize(1);
        assertThat(embeddings.get(0).vector()).hasSize(1536).containsExactly(embedding);

        var tokenUsage = response.tokenUsage();
        // the mock counts the words of the input text ("one"), the reply does not script a token count
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(1);
        assertThat(tokenUsage.outputTokenCount()).isNull();
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(1);

        CapturedRequest request = mock.embeddings().singleRequest();
        assertThat(request.path()).isEqualTo("/bedrock/model/amazon.titan-embed-text-v1/invoke");
        assertThat(request.bodyAsJson().at("/inputText").asText()).isEqualTo("one");
    }

    private static float[] generateEmbedding(int length) {
        float[] embedding = new float[length];
        for (int i = 0; i < length; i++) {
            embedding[i] = (float) i / length;
        }
        return embedding;
    }
}
