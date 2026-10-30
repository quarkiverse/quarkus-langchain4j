package io.quarkiverse.langchain4j.ollama.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
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
import dev.langchain4j.model.output.Response;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class OllamaEmbeddingModelTokenUsageTest extends WiremockAware {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.ollama.base-url", WiremockAware.wiremockUrlForConfig())
            .overrideConfigKey("quarkus.langchain4j.ollama.embedding-model.model-id", "nomic-embed-text")
            .overrideConfigKey("quarkus.langchain4j.devservices.enabled", "false");

    @Inject
    EmbeddingModel embeddingModel;

    @Test
    void shouldReportTokenUsageFromPromptEvalCount() {
        wiremock().register(
                post(urlEqualTo("/api/embed"))
                        .withRequestBody(matchingJsonPath("$.model", equalTo("nomic-embed-text")))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                        {
                                          "model": "nomic-embed-text",
                                          "embeddings": [[0.1, 0.2, 0.3]],
                                          "total_duration": 14143917,
                                          "load_duration": 1019500,
                                          "prompt_eval_count": 8
                                        }
                                        """)));

        Response<Embedding> response = embeddingModel.embed("Hello there");

        assertThat(response.content().vector()).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(response.tokenUsage()).isNotNull();
        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(8);
        assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(8);
    }

    @Test
    void shouldSumTokenUsageAcrossSegments() {
        wiremock().register(
                post(urlEqualTo("/api/embed"))
                        .withRequestBody(matchingJsonPath("$.input", equalTo("first")))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                        {
                                          "model": "nomic-embed-text",
                                          "embeddings": [[0.1, 0.2]],
                                          "prompt_eval_count": 3
                                        }
                                        """)));
        wiremock().register(
                post(urlEqualTo("/api/embed"))
                        .withRequestBody(matchingJsonPath("$.input", equalTo("second")))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                        {
                                          "model": "nomic-embed-text",
                                          "embeddings": [[0.3, 0.4]],
                                          "prompt_eval_count": 5
                                        }
                                        """)));

        Response<List<Embedding>> response = embeddingModel
                .embedAll(List.of(TextSegment.from("first"), TextSegment.from("second")));

        assertThat(response.content()).hasSize(2);
        assertThat(response.tokenUsage()).isNotNull();
        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(8);
    }

    @Test
    void shouldNotFailWhenPromptEvalCountIsAbsent() {
        wiremock().register(
                post(urlEqualTo("/api/embed"))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                        {
                                          "model": "nomic-embed-text",
                                          "embeddings": [[0.1, 0.2, 0.3]]
                                        }
                                        """)));

        Response<Embedding> response = embeddingModel.embed("Hello there");

        assertThat(response.content().vector()).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(response.tokenUsage()).isNull();
    }
}
