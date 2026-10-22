package io.quarkiverse.langchain4j.vertexai.gemini.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.ProcessingException;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.embedding.EmbeddingModel;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class VertexAiGeminiEmbeddingModelTimeoutTest extends WiremockAware {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.gemini.base-url", WiremockAware.wiremockUrlForConfig())
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.gemini.location", "test-location")
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.gemini.project-id", "test-project")
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.gemini.embedding-model.timeout", "1s");

    @Inject
    EmbeddingModel embeddingModel;

    @Test
    void configuredTimeoutIsApplied() {
        wiremock().register(post(urlPathEqualTo(
                "/v1/projects/test-project/locations/test-location/publishers/google/models/text-embedding-004:predict"))
                .willReturn(aResponse()
                        .withFixedDelay(3000)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"predictions":[{"embeddings":{"values":[0.1,0.2]}}]}
                                """)));

        assertThatThrownBy(() -> embeddingModel.embed("Hello"))
                .isInstanceOf(ProcessingException.class)
                .hasMessageContaining("timeout period of 1000ms has been exceeded");
    }

    @Singleton
    public static class DummyAuthProvider implements ModelAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            return "Bearer somekey";
        }
    }
}
