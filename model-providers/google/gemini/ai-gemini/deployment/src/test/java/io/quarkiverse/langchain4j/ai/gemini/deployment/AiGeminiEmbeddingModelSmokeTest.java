package io.quarkiverse.langchain4j.ai.gemini.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusUnitTest;

public class AiGeminiEmbeddingModelSmokeTest extends WiremockAware {

    private static final String API_KEY = "dummy";
    private static final String EMBED_MODEL_ID = "text-embedding-004";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.ai.gemini.base-url", WiremockAware.wiremockUrlForConfig())
            .overrideRuntimeConfigKey("quarkus.langchain4j.ai.gemini.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.ai.gemini.log-requests", "true");

    @Inject
    EmbeddingModel embeddingModel;

    @Test
    void testBatch() {
        wiremock().register(
                post(urlEqualTo(
                        String.format("/models/%s:batchEmbedContents", EMBED_MODEL_ID)))
                        .withHeader("x-goog-api-key", equalTo(API_KEY))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                         {
                                           "embeddings": [
                                             {
                                               "values": [
                                                 -0.010632273,
                                                 0.019375853,
                                                 0.020965198,
                                                 0.0007706437,
                                                 -0.061464068,
                                                 -0.007153866,
                                                 -0.028534686
                                               ]
                                             },
                                             {
                                               "values": [
                                                 0.018468002,
                                                 0.0054281265,
                                                 -0.017658807,
                                                 0.013859263,
                                                 0.05341865,
                                                 0.026714388,
                                                 0.0018762478
                                               ]
                                             }
                                           ]
                                          }
                                        """)));

        List<TextSegment> textSegments = List.of(TextSegment.from("Hello"), TextSegment.from("Bye"));
        Response<List<Embedding>> response = embeddingModel.embedAll(textSegments);

        assertThat(response.content()).hasSize(2);
    }

    @Test
    void test() {
        assertThat(ClientProxy.unwrap(embeddingModel)).isInstanceOf(GoogleAiEmbeddingModel.class);

        wiremock().register(
                post(urlEqualTo(
                        String.format("/models/%s:embedContent", EMBED_MODEL_ID)))
                        .withHeader("x-goog-api-key", equalTo(API_KEY))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                         {
                                            "embedding": {
                                              "values": [
                                                0.013168517,
                                                -0.00871193,
                                                -0.046782672,
                                                0.00069969177,
                                                -0.009518872,
                                                -0.008720178,
                                                0.06010358
                                                ]
                                            }
                                         }
                                        """)));

        float[] response = embeddingModel.embed("Hello World").content().vector();
        assertThat(response).hasSize(7);
    }
}
