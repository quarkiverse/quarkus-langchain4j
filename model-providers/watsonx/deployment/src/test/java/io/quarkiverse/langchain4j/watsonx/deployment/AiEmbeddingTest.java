package io.quarkiverse.langchain4j.watsonx.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Date;
import java.util.List;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.quarkiverse.langchain4j.watsonx.bean.EmbeddingRequest;
import io.quarkus.test.QuarkusUnitTest;

public class AiEmbeddingTest extends WireMockAbstract {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", WireMockUtil.URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", WireMockUtil.URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", WireMockUtil.API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", WireMockUtil.PROJECT_ID)
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WireMockUtil.class));

    @Inject
    ChatLanguageModel model;

    @Inject
    EmbeddingModel embeddingModel;

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

        mockServers.mockIAMBuilder(200)
                .response(WireMockUtil.BEARER_TOKEN, new Date())
                .build();

        var input = "Embedding THIS!";
        EmbeddingRequest request = new EmbeddingRequest(WireMockUtil.DEFAULT_EMBEDDING_MODEL, WireMockUtil.PROJECT_ID,
                List.of(input, input, input), null);

        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_EMBEDDING_API, 200)
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
                        """.formatted(WireMockUtil.DEFAULT_EMBEDDING_MODEL))
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

    private List<Float> mockEmbeddingServer(String input) throws Exception {
        mockServers.mockIAMBuilder(200)
                .response(WireMockUtil.BEARER_TOKEN, new Date())
                .build();

        EmbeddingRequest request = new EmbeddingRequest(WireMockUtil.DEFAULT_EMBEDDING_MODEL, WireMockUtil.PROJECT_ID,
                List.of(input), null);

        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_EMBEDDING_API, 200)
                .body(mapper.writeValueAsString(request))
                .response(WireMockUtil.RESPONSE_WATSONX_EMBEDDING_API)
                .build();

        return List.of(-0.006929283f, -0.005336422f, -0.024047505f);
    }
}
