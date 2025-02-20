package io.quarkiverse.langchain4j.ai.gemini.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.embedding.EmbeddingModel;
import io.quarkiverse.langchain4j.ai.runtime.gemini.AiGeminiEmbeddingModel;
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
            .overrideRuntimeConfigKey("quarkus.langchain4j.ai.gemini.log-requests", "true");

    @Inject
    EmbeddingModel embeddingModel;

    @Test
    void test() {
        assertThat(ClientProxy.unwrap(embeddingModel)).isInstanceOf(AiGeminiEmbeddingModel.class);

        wiremock().register(
                post(urlEqualTo(
                        String.format("/v1beta/models/%s:embedContent?key=%s",
                                EMBED_MODEL_ID, API_KEY)))
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
