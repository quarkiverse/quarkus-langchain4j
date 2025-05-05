package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.BEARER_TOKEN;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.DEFAULT_EMBEDDING_MODEL;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.PROJECT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.RESPONSE_WATSONX_EMBEDDING_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_IAM_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_EMBEDDING_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_SERVER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Date;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.stubbing.Scenario;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.quarkiverse.langchain4j.watsonx.bean.EmbeddingRequest;
import io.quarkus.test.QuarkusUnitTest;

public class AiEmbeddingTest extends WireMockAbstract {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WireMockUtil.class));

    @Inject
    ChatModel model;

    @Inject
    EmbeddingModel embeddingModel;

    @Override
    void handlerBeforeEach() throws Exception {
        mockIAMBuilder(200)
                .response(BEARER_TOKEN, new Date())
                .build();
    }

    @Test
    void test_embed_text() throws Exception {
        var input = "Embedding THIS!";
        var vector = mockEmbeddingServer(input);

        Response<Embedding> response = embeddingModel.embed(input);
        assertNotNull(response);
        assertNotNull(response.content());
        assertEquals(vector.size(), response.content().vectorAsList().size());
        assertEquals(vector, response.content().vectorAsList());
    }

    @Test
    void test_embed_textsegment() throws Exception {
        var input = "Embedding THIS!";
        var vector = mockEmbeddingServer(input);

        Response<Embedding> response = embeddingModel.embed(TextSegment.textSegment(input));
        assertNotNull(response);
        assertNotNull(response.content());
        assertEquals(vector.size(), response.content().vectorAsList().size());
        assertEquals(vector, response.content().vectorAsList());
    }

    @Test
    void test_embed_list_of_one_textsegment() throws Exception {
        var input = "Embedding THIS!";
        var vector = mockEmbeddingServer(input);

        Response<List<Embedding>> response = embeddingModel.embedAll(List.of(TextSegment.textSegment(input)));
        assertNotNull(response);
        assertNotNull(response.content());
        assertEquals(1, response.content().size());
        assertEquals(vector.size(), response.content().get(0).vectorAsList().size());
        assertEquals(vector, response.content().get(0).vectorAsList());
    }

    @Test
    void test_embed_list_of_three_textsegment() throws Exception {

        var input = "Embedding THIS!";
        EmbeddingRequest request = new EmbeddingRequest(DEFAULT_EMBEDDING_MODEL, null, PROJECT_ID,
                List.of(input, input, input), null);

        mockWatsonxBuilder(URL_WATSONX_EMBEDDING_API, 200)
                .body(mapper.writeValueAsString(request))
                .response("""
                        {
                            "model_id": "%s",
                            "results": [
                              {
                                "embedding": [
                                  -0.006929283,
                                  -0.005336422,
                                  -0.024047505
                                ]
                              },
                              {
                                "embedding": [
                                  -0.006929283,
                                  -0.005336422,
                                  -0.024047505
                                ]
                              },
                              {
                                "embedding": [
                                  -0.006929283,
                                  -0.005336422,
                                  -0.024047505
                                ]
                              }
                            ],
                            "created_at": "2024-02-21T17:32:28Z",
                            "input_token_count": 10
                        }
                        """.formatted(DEFAULT_EMBEDDING_MODEL))
                .build();

        var vector = List.of(-0.006929283f, -0.005336422f, -0.024047505f);

        Response<List<Embedding>> response = embeddingModel.embedAll(
                List.of(TextSegment.textSegment(input), TextSegment.textSegment(input), TextSegment.textSegment(input)));
        assertNotNull(response);
        assertNotNull(response.content());
        assertEquals(3, response.content().size());
        assertEquals(3, response.content().get(0).vectorAsList().size());
        assertEquals(3, response.content().get(1).vectorAsList().size());
        assertEquals(3, response.content().get(2).vectorAsList().size());
        assertEquals(vector, response.content().get(0).vectorAsList());
        assertEquals(vector, response.content().get(1).vectorAsList());
        assertEquals(vector, response.content().get(2).vectorAsList());
    }

    @Test
    public void test_high_embedding_text_segments() throws Exception {
        var RESPONSE = """
                {
                    "model_id": "%s",
                    "results": [],
                    "created_at": "2024-02-21T17:32:28Z",
                    "input_token_count": 10
                }
                """;

        Function<List<String>, EmbeddingRequest> createRequest = (List<String> elementsToEmbed) -> {
            return new EmbeddingRequest(DEFAULT_EMBEDDING_MODEL, null, PROJECT_ID, elementsToEmbed,
                    null);
        };

        var list = IntStream.rangeClosed(1, 2001).mapToObj(String::valueOf).collect(Collectors.toList());

        mockWatsonxBuilder(URL_WATSONX_EMBEDDING_API, 200)
                .scenario(Scenario.STARTED, "SECOND_CALL")
                .body(mapper.writeValueAsString(createRequest.apply(list.subList(0, 1000))))
                .response(RESPONSE.formatted(DEFAULT_EMBEDDING_MODEL))
                .build();

        mockWatsonxBuilder(URL_WATSONX_EMBEDDING_API, 200)
                .scenario("SECOND_CALL", "THIRD_CALL")
                .body(mapper.writeValueAsString(createRequest.apply(list.subList(1000, 2000))))
                .response(RESPONSE.formatted(DEFAULT_EMBEDDING_MODEL))
                .build();

        mockWatsonxBuilder(URL_WATSONX_EMBEDDING_API, 200)
                .scenario("THIRD_CALL", Scenario.STARTED)
                .body(mapper.writeValueAsString(createRequest.apply(list.subList(2000, 2001))))
                .response(RESPONSE.formatted(DEFAULT_EMBEDDING_MODEL))
                .build();

        embeddingModel.embedAll(list.stream().map(TextSegment::textSegment).toList());
    }

    private List<Float> mockEmbeddingServer(String input) throws Exception {
        mockIAMBuilder(200)
                .response(BEARER_TOKEN, new Date())
                .build();

        EmbeddingRequest request = new EmbeddingRequest(DEFAULT_EMBEDDING_MODEL, null, PROJECT_ID,
                List.of(input), null);

        mockWatsonxBuilder(URL_WATSONX_EMBEDDING_API, 200)
                .body(mapper.writeValueAsString(request))
                .response(RESPONSE_WATSONX_EMBEDDING_API)
                .build();

        return List.of(-0.006929283f, -0.005336422f, -0.024047505f);
    }
}
