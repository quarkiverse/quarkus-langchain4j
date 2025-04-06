package io.quarkiverse.langchain4j.bedrock.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.bedrock.BedrockTitanEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusUnitTest;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BedrockEmbeddingModelTitanTest extends BedrockTestBase {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(TestCredentialsProvider.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.embedding-model.model-id", "amazon.titan-embed-text-v1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.embedding-model.aws.region", "eu-central-1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.embedding-model.aws.endpoint-override",
                    "http://localhost:%d".formatted(WM_PORT))
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.embedding-model.aws.credentials-provider",
                    "TestCredentialsProvider")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.log-requests", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.log-responses", "true")
            .overrideRuntimeConfigKey("quarkus.log.category.\"io.quarkiverse.langchain4j.bedrock\".level", "DEBUG");

    @Inject
    EmbeddingModel embeddingModel;

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
        double[] embedding = generateEmbedding(1536);
        var embeddingStr = DoubleStream.of(embedding).mapToObj(Double::toString).collect(Collectors.joining(","));

        stubFor(post(anyUrl()) //
                .willReturn(aResponse() //
                        .withStatus(200) //
                        .withHeader("Content-Type", "application/json") //
                        .withBody("""
                                {
                                  "embedding": [%s],
                                  "inputTextTokenCount": 1
                                }
                                """.formatted(embeddingStr))));

        // when
        var response = embeddingModel.embedAll(segments);

        // then
        assertThat(response).isNotNull();
        assertThat(response.finishReason()).isNull();

        var embeddings = response.content();
        assertThat(embeddings).hasSize(1);
        assertThat(embeddings.get(0).vector()).hasSize(1536);

        var tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(1);
        assertThat(tokenUsage.outputTokenCount()).isNull();
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(1);
    }

    private static double[] generateEmbedding(int length) {
        double[] embedding = new double[length];
        Random random = new Random();
        for (int i = 0; i < length; i++) {
            embedding[i] = random.nextDouble();
        }
        return embedding;
    }
}
