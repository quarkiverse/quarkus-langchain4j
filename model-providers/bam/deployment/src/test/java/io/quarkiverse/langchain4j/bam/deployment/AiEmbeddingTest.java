package io.quarkiverse.langchain4j.bam.deployment;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.quarkiverse.langchain4j.bam.BamException;
import io.quarkiverse.langchain4j.bam.BamRestApi;
import io.quarkiverse.langchain4j.bam.EmbeddingRequest;
import io.quarkiverse.langchain4j.bam.runtime.config.LangChain4jBamConfig;
import io.quarkus.test.QuarkusUnitTest;

public class AiEmbeddingTest {

    static WireMockServer wireMockServer;
    static ObjectMapper mapper;
    static WireMockUtil mockServers;

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.base-url", WireMockUtil.URL)
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.api-key", WireMockUtil.API_KEY)
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WireMockUtil.class));

    @BeforeAll
    static void beforeAll() {
        wireMockServer = new WireMockServer(options().port(WireMockUtil.PORT));
        wireMockServer.start();
        mapper = BamRestApi.objectMapper(new ObjectMapper());
        mockServers = new WireMockUtil(wireMockServer);
    }

    @AfterAll
    static void afterAll() {
        wireMockServer.stop();
    }

    @Inject
    LangChain4jBamConfig langchain4jBamConfig;

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
        var input = "Embedding THIS!";
        var config = langchain4jBamConfig.defaultConfig();
        var modelId = config.embeddingModel().modelId();

        EmbeddingRequest body = new EmbeddingRequest(modelId, List.of(input, input, input));

        mockServers
                .mockBuilder(WireMockUtil.URL_EMBEDDING_API, 200)
                .body(mapper.writeValueAsString(body))
                .response("""
                            {
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
                                ]
                            }
                        """)
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
    void test_embedding_model_not_found() throws Exception {
        mockServers
                .mockBuilder(WireMockUtil.URL_EMBEDDING_API, 404)
                .response("""
                        {
                            "status_code": 404,
                            "error": "Not Found",
                            "message": "Model not found",
                            "extensions": {
                                "code": "NOT_FOUND"
                            }
                        }
                        """)
                .build();

        var ex = assertThrowsExactly(BamException.class, () -> embeddingModel.embed("test"));
        assertEquals(404, ex.getStatusCode());
        assertEquals("Not Found", ex.getError());
        assertEquals("Model not found", ex.getMessage());
        assertTrue(ex.getExtensions().isPresent());
        assertEquals(BamException.Code.NOT_FOUND, ex.getExtensions().get().getCode());
    }

    private List<Float> mockEmbeddingServer(String input) throws Exception {
        var config = langchain4jBamConfig.defaultConfig();
        var modelId = config.embeddingModel().modelId();

        EmbeddingRequest body = new EmbeddingRequest(modelId, List.of(input));

        mockServers
                .mockBuilder(WireMockUtil.URL_EMBEDDING_API, 200)
                .body(mapper.writeValueAsString(body))
                .response("""
                            {
                                "results": [
                                        {
                                            "embedding": [
                                              -0.006929283,
                                              -0.005336422,
                                              -0.024047505
                                            ]
                                        }
                                    ]
                            }
                        """)
                .build();

        return List.of(-0.006929283f, -0.005336422f, -0.024047505f);
    }
}
