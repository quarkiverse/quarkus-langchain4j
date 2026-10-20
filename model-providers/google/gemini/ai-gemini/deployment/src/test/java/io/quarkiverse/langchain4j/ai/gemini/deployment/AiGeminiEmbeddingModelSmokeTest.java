package io.quarkiverse.langchain4j.ai.gemini.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.inject.Inject;

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
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.EmbeddingReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;

public class AiGeminiEmbeddingModelSmokeTest {

    private static final String API_KEY = "dummy";
    private static final String EMBED_MODEL_ID = "text-embedding-004";
    private static final String TASK_TYPE = "RETRIEVAL_QUERY";
    private static final int OUTPUT_DIMENSION = 7;
    private static final float[] SINGLE_VECTOR = { 0.013168517f, -0.00871193f, -0.046782672f, 0.00069969177f,
            -0.009518872f, -0.008720178f, 0.06010358f };

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.ai.gemini.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.ai.gemini.embedding-model.task-type", TASK_TYPE)
            .overrideRuntimeConfigKey("quarkus.langchain4j.ai.gemini.embedding-model.output-dimension",
                    String.valueOf(OUTPUT_DIMENSION))
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
        mock.embeddings().reply(EmbeddingReply.of(
                new float[] { -0.010632273f, 0.019375853f, 0.020965198f, 0.0007706437f, -0.061464068f, -0.007153866f,
                        -0.028534686f },
                new float[] { 0.018468002f, 0.0054281265f, -0.017658807f, 0.013859263f, 0.05341865f, 0.026714388f,
                        0.0018762478f }));

        List<TextSegment> textSegments = List.of(TextSegment.from("Hello"), TextSegment.from("Bye"));
        Response<List<Embedding>> response = embeddingModel.embedAll(textSegments);

        assertThat(response.content()).hasSize(2);

        CapturedRequest request = mock.embeddings().singleRequest();
        assertThat(request.path())
                .isEqualTo(String.format("/ai-gemini/v1beta/models/%s:batchEmbedContents", EMBED_MODEL_ID));
        assertThat(request.query()).isNull();
        assertThat(request.header("x-goog-api-key")).isEqualTo(API_KEY);
    }

    @Test
    void test() {
        assertThat(ClientProxy.unwrap(embeddingModel)).isInstanceOf(GoogleAiEmbeddingModel.class);

        mock.embeddings().reply(EmbeddingReply.of(SINGLE_VECTOR));

        float[] response = embeddingModel.embed("Hello World").content().vector();
        assertThat(response).hasSize(7);

        CapturedRequest request = mock.embeddings().singleRequest();
        assertThat(request.path()).isEqualTo(String.format("/ai-gemini/v1beta/models/%s:embedContent", EMBED_MODEL_ID));
        assertThat(request.query()).isNull();
        assertThat(request.header("x-goog-api-key")).isEqualTo(API_KEY);
    }

    @Test
    void testTaskTypeAndOutputDimensionAreAppliedToRequest() {
        mock.embeddings().reply(EmbeddingReply.of(SINGLE_VECTOR));

        embeddingModel.embed("Hello World").content().vector();

        CapturedRequest request = mock.embeddings().singleRequest();
        assertThat(request.path()).isEqualTo(String.format("/ai-gemini/v1beta/models/%s:embedContent", EMBED_MODEL_ID));
        assertThat(request.query()).isNull();
        assertThat(request.header("x-goog-api-key")).isEqualTo(API_KEY);
        String requestBody = request.body();
        assertThat(requestBody).matches("(?s).*\"taskType\"\\s*:\\s*\"RETRIEVAL_QUERY\".*");
        assertThat(requestBody).matches("(?s).*\"outputDimensionality\"\\s*:\\s*\"?7\"?.*");
    }
}
