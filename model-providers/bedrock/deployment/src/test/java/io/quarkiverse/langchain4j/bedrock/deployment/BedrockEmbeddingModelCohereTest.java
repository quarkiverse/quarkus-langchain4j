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
import dev.langchain4j.model.bedrock.BedrockCohereEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusUnitTest;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BedrockEmbeddingModelCohereTest extends BedrockTestBase {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(TestCredentialsProvider.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.embedding-model.model-id", "cohere.embed-english-v3")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.embedding-model.cohere.input-type", "search_query")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.embedding-model.client.region", "eu-central-1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.embedding-model.client.endpoint-override",
                    "http://localhost:%d".formatted(WM_PORT))
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.embedding-model.client.credentials-provider",
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
        assertThat(ClientProxy.unwrap(embeddingModel)).isInstanceOf(BedrockCohereEmbeddingModel.class);
    }

    @Test
    void should_embed_one() {
        // given
        var segments = List.of(TextSegment.from("one"));
        double[] embedding = generateEmbedding(1024);
        var embeddingStr = DoubleStream.of(embedding).mapToObj(d -> (float) d).map(Object::toString)
                .collect(Collectors.joining(","));

        stubFor(post(anyUrl()) //
                .willReturn(aResponse() //
                        .withStatus(200) //
                        .withHeader("Content-Type", "application/json") //
                        .withBody("""
                                {
                                  "embeddings": {
                                    "float": [[%s]]
                                  },
                                  "response_type" : "embeddings_floats",
                                  "texts": ["one"]
                                }
                                """.formatted(embeddingStr))));

        // when
        var response = embeddingModel.embedAll(segments);

        // then
        assertThat(response).isNotNull();
        assertThat(response.finishReason()).isNull();

        var embeddings = response.content();
        assertThat(embeddings).hasSize(1);
        assertThat(embeddings.get(0).vector()).hasSize(1024);

        var tokenUsage = response.tokenUsage();
        assertThat(tokenUsage).isNull();
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
