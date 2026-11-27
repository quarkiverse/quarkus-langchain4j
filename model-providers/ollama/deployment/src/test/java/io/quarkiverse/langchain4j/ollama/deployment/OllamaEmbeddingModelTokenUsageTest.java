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
import io.quarkus.test.QuarkusExtensionTest;
import tools.jackson.databind.JsonNode;

/**
 * Raw stubs: the mock derives {@code prompt_eval_count} from the number of inputs, while these tests need specific
 * counts, and one needs the field absent.
 */
public class OllamaEmbeddingModelTokenUsageTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.ollama.embedding-model.model-id", "nomic-embed-text")
            .overrideConfigKey("quarkus.langchain4j.devservices.enabled", "false");

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void shouldReportTokenUsageFromPromptEvalCount() {
        mock.raw().on("POST", "/ollama/api/embed").reply(200, """
                {
                  "model": "nomic-embed-text",
                  "embeddings": [[0.1, 0.2, 0.3]],
                  "total_duration": 14143917,
                  "load_duration": 1019500,
                  "prompt_eval_count": 8
                }
                """);

        Response<Embedding> response = embeddingModel.embed("Hello there");

        assertThat(response.content().vector()).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(response.tokenUsage()).isNotNull();
        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(8);
        assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(8);

        assertThat(mock.requests()).singleElement()
                .satisfies(r -> assertThat(r.bodyAsJson().at("/model").asText()).isEqualTo("nomic-embed-text"));
    }

    @Test
    void shouldReportTokenUsageOfTheBatchedRequest() {
        // Ollama reports one prompt_eval_count for the whole batch
        mock.raw().on("POST", "/ollama/api/embed").reply(200, """
                {
                  "model": "nomic-embed-text",
                  "embeddings": [[0.1, 0.2], [0.3, 0.4]],
                  "prompt_eval_count": 8
                }
                """);

        Response<List<Embedding>> response = embeddingModel
                .embedAll(List.of(TextSegment.from("first"), TextSegment.from("second")));

        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(0).vector()).containsExactly(0.1f, 0.2f);
        assertThat(response.content().get(1).vector()).containsExactly(0.3f, 0.4f);
        assertThat(response.tokenUsage()).isNotNull();
        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(8);

        assertThat(mock.requests()).singleElement()
                .satisfies(r -> assertThat(r.bodyAsJson().at("/input")).extracting(JsonNode::asText)
                        .containsExactly("first", "second"));
    }

    @Test
    void shouldNotFailWhenPromptEvalCountIsAbsent() {
        mock.raw().on("POST", "/ollama/api/embed").reply(200, """
                {
                  "model": "nomic-embed-text",
                  "embeddings": [[0.1, 0.2, 0.3]]
                }
                """);

        Response<Embedding> response = embeddingModel.embed("Hello there");

        assertThat(response.content().vector()).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(response.tokenUsage()).isNull();

        assertThat(mock.requests()).singleElement().extracting(CapturedRequest::path).isEqualTo("/ollama/api/embed");
    }
}
