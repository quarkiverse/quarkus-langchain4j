package com.ibm.langchain4j.watsonx.deployment;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;

import org.jboss.resteasy.reactive.ClientWebApplicationException;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.quarkiverse.langchain4j.watsonx.client.WatsonxRestApi;
import io.quarkiverse.langchain4j.watsonx.runtime.config.LangChain4jWatsonxConfig;
import io.quarkus.test.QuarkusUnitTest;

public class CacheTokenTest {

    static WireMockServer watsonxServer;
    static WireMockServer iamServer;
    static ObjectMapper mapper;

    @Inject
    LangChain4jWatsonxConfig config;

    @Inject
    ChatLanguageModel chatModel;

    @Inject
    EmbeddingModel embeddingModel;

    static WireMockUtil mockServers;

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", WireMockUtil.URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", WireMockUtil.URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", WireMockUtil.API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", WireMockUtil.PROJECT_ID)
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WireMockUtil.class));

    @BeforeAll
    static void beforeAll() {
        mapper = WatsonxRestApi.objectMapper(new ObjectMapper());

        watsonxServer = new WireMockServer(options().port(WireMockUtil.PORT_WATSONX_SERVER));
        watsonxServer.start();

        iamServer = new WireMockServer(options().port(WireMockUtil.PORT_IAM_SERVER));
        iamServer.start();

        mockServers = new WireMockUtil(watsonxServer, iamServer);
    }

    @AfterAll
    static void afterAll() {
        watsonxServer.stop();
        iamServer.stop();
    }

    @Test
    void try_token_chat_cache() throws InterruptedException {

        // Create a token which expires in 3 seconds.
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.SECOND, 3);
        Date date = calendar.getTime();

        // First call returns 200.
        mockServers.mockIAMBuilder(200)
                .scenario(Scenario.STARTED, "error")
                .response("3secondstoken", date)
                .build();

        // All other call after 3 seconds they will give an error.
        mockServers.mockIAMBuilder(500)
                .responseMediaType(MediaType.TEXT_PLAIN)
                .scenario("error", Scenario.STARTED)
                .response("3 seconds are passed")
                .build();

        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_CHAT_API, 200)
                .token("3secondstoken")
                .response("""
                            {
                                "model_id": "meta-llama/llama-2-70b-chat",
                                "created_at": "2024-01-21T17:06:14.052Z",
                                "results": [
                                    {
                                        "generated_text": "AI Response",
                                        "generated_token_count": 5,
                                        "input_token_count": 50,
                                        "stop_reason": "eos_token",
                                        "seed": 2123876088
                                    }
                                ]
                            }
                        """)
                .build();

        // First: call to mockIAMServer.
        assertEquals("AI Response", chatModel.generate("message"));

        // Second: uses the cache.
        assertEquals("AI Response", chatModel.generate("message"));

        Thread.sleep(3000);

        // Third: now an error should appear.
        WebApplicationException ex = assertThrowsExactly(ClientWebApplicationException.class,
                () -> chatModel.generate("message"));
        assertEquals(500, ex.getResponse().getStatus());
        assertTrue(ex.getMessage().contains("3 seconds are passed"));
    }

    @Test
    void try_token_embedding_cache() throws InterruptedException {

        // Create a token which expires in 3 seconds.
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.SECOND, 3);
        Date date = calendar.getTime();

        // First call returns 200.
        mockServers.mockIAMBuilder(200)
                .scenario(Scenario.STARTED, "error")
                .response("3secondstoken", date)
                .build();

        // All other call after 3 seconds they will give an error.
        mockServers.mockIAMBuilder(500)
                .responseMediaType(MediaType.TEXT_PLAIN)
                .scenario("error", Scenario.STARTED)
                .response("3 seconds are passed")
                .build();

        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_EMBEDDING_API, 200)
                .token("3secondstoken")
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
                              }
                            ],
                            "created_at": "2024-02-21T17:32:28Z",
                            "input_token_count": 10
                          }
                        """.formatted(WireMockUtil.DEFAULT_EMBEDDING_MODEL))
                .build();

        var vector = List.of(-0.006929283f, -0.005336422f, -0.024047505f);

        // First: call to mockIAMServer.
        assertEquals(vector, embeddingModel.embed("message").content().vectorAsList());

        // Second: uses the cache.
        assertEquals(vector, embeddingModel.embed("message").content().vectorAsList());

        Thread.sleep(3000);

        // Third: now an error should appear.
        WebApplicationException ex = assertThrowsExactly(ClientWebApplicationException.class,
                () -> embeddingModel.embed("message"));
        assertEquals(500, ex.getResponse().getStatus());
        assertTrue(ex.getMessage().contains("3 seconds are passed"));
    }
}
