package io.quarkiverse.langchain4j.vertexai.gemini.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.ProcessingException;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.embedding.EmbeddingModel;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class VertexAiGeminiEmbeddingModelTimeoutTest {

    private static final String EMBEDDING_PATH = "/vertexai-gemini/v1/projects/test-project/locations/test-location/publishers/google/models/text-embedding-004:predict";

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            // set at build time, so the mock sees them as configured and does not fill in its own placeholders
            .overrideConfigKey("quarkus.langchain4j.vertexai.gemini.location", "test-location")
            .overrideConfigKey("quarkus.langchain4j.vertexai.gemini.project-id", "test-project")
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.gemini.embedding-model.timeout", "1s");

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void setUp() {
        mock.reset();
    }

    @Test
    void configuredTimeoutIsApplied() {
        mock.raw().on("POST", EMBEDDING_PATH).delay(Duration.ofSeconds(3)).reply(200, """
                {"predictions":[{"embeddings":{"values":[0.1,0.2]}}]}
                """);

        assertThatThrownBy(() -> embeddingModel.embed("Hello"))
                .isInstanceOf(ProcessingException.class)
                .hasMessageContaining("timeout period of 1000ms has been exceeded");

        assertThat(mock.requests()).singleElement()
                .satisfies(request -> assertThat(request.path()).isEqualTo(EMBEDDING_PATH));
    }

    @Singleton
    public static class DummyAuthProvider implements ModelAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            return "Bearer somekey";
        }
    }
}
