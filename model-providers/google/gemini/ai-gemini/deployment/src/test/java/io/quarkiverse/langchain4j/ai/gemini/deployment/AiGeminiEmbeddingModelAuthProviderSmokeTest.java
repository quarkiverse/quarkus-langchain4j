package io.quarkiverse.langchain4j.ai.gemini.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.EmbeddingReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;

public class AiGeminiEmbeddingModelAuthProviderSmokeTest {

    private static final String API_KEY = "dummy";
    private static final String EMBED_MODEL_ID = "text-embedding-004";
    private static final float[] VECTOR = { -0.010632273f, 0.019375853f, 0.020965198f, 0.0007706437f, -0.061464068f,
            -0.007153866f, -0.028534686f };

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            // set to empty on purpose: the provider installs its ModelAuthProvider filter only without an api key, and the
            // mock leaves a key alone when it is set, even to an empty value
            .overrideConfigKey("quarkus.langchain4j.ai.gemini.api-key", "")
            .overrideRuntimeConfigKey("quarkus.langchain4j.ai.gemini.log-requests", "true");

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void testBatch() {
        mock.embeddings().reply(EmbeddingReply.of(VECTOR, VECTOR));

        List<TextSegment> textSegments = List.of(TextSegment.from("Hello"), TextSegment.from("Bye"));
        Response<List<Embedding>> response = embeddingModel.embedAll(textSegments);

        assertThat(response.content()).hasSize(2);

        CapturedRequest request = mock.embeddings().singleRequest();
        assertThat(request.path())
                .isEqualTo(String.format("/ai-gemini/v1beta/models/%s:batchEmbedContents", EMBED_MODEL_ID));
        assertThat(request.header("Authorization")).isEqualTo("Bearer " + API_KEY);
    }

    @Test
    void test() {
        assertThat(ClientProxy.unwrap(embeddingModel)).isInstanceOf(GoogleAiEmbeddingModel.class);

        mock.embeddings().reply(EmbeddingReply.of(VECTOR));

        float[] response = embeddingModel.embed("Hello World").content().vector();
        assertThat(response).hasSize(7);

        CapturedRequest request = mock.embeddings().singleRequest();
        assertThat(request.path()).isEqualTo(String.format("/ai-gemini/v1beta/models/%s:embedContent", EMBED_MODEL_ID));
        assertThat(request.header("Authorization")).isEqualTo("Bearer " + API_KEY);
    }

    @Singleton
    public static class DummyAuthProvider implements ModelAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            return "Bearer " + API_KEY;
        }
    }
}
