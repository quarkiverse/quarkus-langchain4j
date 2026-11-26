package io.quarkiverse.langchain4j.ollama.deployment;

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
import dev.langchain4j.model.output.Response;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkiverse.langchain4j.mockapi.RawResponse;
import io.quarkus.test.QuarkusExtensionTest;
import tools.jackson.databind.JsonNode;

/**
 * {@code embedAll} splits its input into requests of at most {@code max-segments-per-batch} segments, so a large
 * ingestion does not turn into a single request whose size grows with the corpus. Raw stubs are used because the
 * mock derives {@code prompt_eval_count} from the number of inputs, while this test needs to see the counts of the
 * individual batches added up.
 */
public class OllamaEmbeddingBatchingTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.devservices.enabled", "false")
            .overrideConfigKey("quarkus.langchain4j.ollama.embedding-model.model-id", "nomic-embed-text")
            .overrideRuntimeConfigKey("quarkus.langchain4j.ollama.embedding-model.max-segments-per-batch", "2");

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void embedAllSplitsIntoBatchesAndKeepsOrder() {
        mock.raw().on("POST", "/ollama/api/embed").replies(
                RawResponse.json(200, """
                        {"model": "nomic-embed-text", "embeddings": [[1, 0], [2, 0]], "prompt_eval_count": 4}
                        """),
                RawResponse.json(200, """
                        {"model": "nomic-embed-text", "embeddings": [[3, 0], [4, 0]], "prompt_eval_count": 4}
                        """),
                RawResponse.json(200, """
                        {"model": "nomic-embed-text", "embeddings": [[5, 0]], "prompt_eval_count": 2}
                        """));

        Response<List<Embedding>> response = embeddingModel.embedAll(List.of(
                TextSegment.from("a"), TextSegment.from("b"), TextSegment.from("c"), TextSegment.from("d"),
                TextSegment.from("e")));

        assertThat(response.content()).extracting(embedding -> embedding.vector()[0])
                .containsExactly(1f, 2f, 3f, 4f, 5f);
        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(10);

        List<CapturedRequest> requests = mock.requests();
        assertThat(requests).hasSize(3);
        assertThat(inputsOf(requests.get(0))).extracting(JsonNode::asText).containsExactly("a", "b");
        assertThat(inputsOf(requests.get(1))).extracting(JsonNode::asText).containsExactly("c", "d");
        assertThat(inputsOf(requests.get(2))).extracting(JsonNode::asText).containsExactly("e");
    }

    @Test
    void inputWithinTheLimitIsOneRequest() {
        mock.raw().on("POST", "/ollama/api/embed").reply(200, """
                {"model": "nomic-embed-text", "embeddings": [[1, 0], [2, 0]], "prompt_eval_count": 4}
                """);

        Response<List<Embedding>> response = embeddingModel.embedAll(List.of(TextSegment.from("a"),
                TextSegment.from("b")));

        assertThat(response.content()).hasSize(2);
        assertThat(mock.requests()).singleElement()
                .satisfies(r -> assertThat(inputsOf(r)).extracting(JsonNode::asText).containsExactly("a", "b"));
    }

    private static JsonNode inputsOf(CapturedRequest request) {
        return request.bodyAsJson().at("/input");
    }
}
