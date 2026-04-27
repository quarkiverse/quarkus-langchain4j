package io.quarkiverse.langchain4j.vertexai.gemini.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkiverse.langchain4j.vertexai.runtime.gemini.VertexAiGeminiEmbeddingModel;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusUnitTest;

public class VertexAiGeminiEmbeddingModelSmokeTest extends WiremockAware {

    private static final String API_KEY = "dummy";
    private static final String EMBED_MODEL_ID = "text-embedding-004";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.gemini.base-url", WiremockAware.wiremockUrlForConfig())
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.gemini.location", "test-location")
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.gemini.project-id", "test-project")
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.gemini.log-requests", "true");

    @Inject
    EmbeddingModel embeddingModel;

    @Test
    void testBatch() {
        wiremock().register(
                post(urlEqualTo(
                        String.format("/v1/projects/test-project/locations/test-location/publishers/google/models/%s:predict",
                                EMBED_MODEL_ID)))
                        .withHeader("Authorization", equalTo("Bearer " + API_KEY))
                        .withRequestBody(equalToJson("""
                                { "instances": [ { "content": "Hello" } ], "parameters": { "autoTruncate": true } }
                                        """))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                         {
                                           "predictions": [
                                             {
                                               "embeddings": {
                                                 "values": [
                                                   -0.010632273,
                                                   0.019375853,
                                                   0.020965198,
                                                   0.0007706437,
                                                   -0.061464068,
                                                   -0.007153866,
                                                   -0.028534686
                                                 ],
                                                 "statistics": {
                                                   "truncated": false,
                                                   "token_count": 1
                                                 }
                                               }
                                             }
                                           ]
                                          }
                                        """)));

        List<TextSegment> textSegments = List.of(TextSegment.from("Hello")); // Only one for gemini-embedding-001
        Response<List<Embedding>> response = embeddingModel.embedAll(textSegments);

        assertThat(response.content()).hasSize(1);
    }

    @Test
    void test() {
        assertThat(ClientProxy.unwrap(embeddingModel)).isInstanceOf(VertexAiGeminiEmbeddingModel.class);

        wiremock().register(
                post(urlEqualTo(
                        String.format("/v1/projects/test-project/locations/test-location/publishers/google/models/%s:predict",
                                EMBED_MODEL_ID)))
                        .withHeader("Authorization", equalTo("Bearer " + API_KEY))
                        .withRequestBody(equalToJson("""
                                { "instances": [ { "content": "Hello World" } ], "parameters": { "autoTruncate": true } }
                                        """))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                         {
                                           "predictions": [
                                             {
                                               "embeddings": {
                                                 "values": [
                                                   0.013168517,
                                                   -0.00871193,
                                                   -0.046782672,
                                                   0.00069969177,
                                                   -0.009518872,
                                                   -0.008720178,
                                                   0.06010358
                                                 ],
                                                 "statistics": {
                                                   "truncated": false,
                                                   "token_count": 2
                                                 }
                                               }
                                             }
                                           ]
                                          }
                                        """)));

        Response<List<Embedding>> response = embeddingModel.embedAll(List.of(TextSegment.from("Hello World")));
        assertThat(response.content().get(0).vector()).hasSize(7);
    }

    @Singleton
    public static class DummyAuthProvider implements ModelAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            return "Bearer " + API_KEY;
        }

    }
}
