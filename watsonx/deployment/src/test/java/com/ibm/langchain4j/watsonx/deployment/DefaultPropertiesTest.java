package com.ibm.langchain4j.watsonx.deployment;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Date;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;

import dev.langchain4j.model.chat.ChatLanguageModel;
import io.quarkiverse.langchain4j.watsonx.bean.Parameters;
import io.quarkiverse.langchain4j.watsonx.bean.TextGenerationRequest;
import io.quarkiverse.langchain4j.watsonx.client.WatsonxRestApi;
import io.quarkiverse.langchain4j.watsonx.runtime.config.LangChain4jWatsonxConfig;
import io.quarkus.test.QuarkusUnitTest;

public class DefaultPropertiesTest {

    static WireMockServer watsonxServer;
    static WireMockServer iamServer;
    static ObjectMapper mapper;

    @Inject
    LangChain4jWatsonxConfig langchain4jWatsonConfig;

    @Inject
    ChatLanguageModel model;

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
    void generate() throws Exception {
        var config = langchain4jWatsonConfig.defaultConfig();
        assertEquals(Duration.ofSeconds(10), config.timeout());
        assertEquals(WireMockUtil.VERSION, config.version());
        assertEquals(false, config.logRequests().orElse(false));
        assertEquals(false, config.logResponses().orElse(false));
        assertEquals(WireMockUtil.DEFAULT_CHAT_MODEL, config.chatModel().modelId());
        assertEquals("greedy", config.chatModel().decodingMethod());
        assertEquals(200, config.chatModel().maxNewTokens());
        assertEquals(0, config.chatModel().minNewTokens());
        assertEquals(false, config.chatModel().randomSeed().isPresent());
        assertEquals(false, config.chatModel().stopSequences().isPresent());
        assertEquals(1.0, config.chatModel().temperature());
        assertTrue(config.chatModel().topK().isEmpty());
        assertTrue(config.chatModel().topP().isEmpty());
        assertTrue(config.chatModel().repetitionPenalty().isEmpty());
        assertTrue(config.chatModel().truncateInputTokens().isEmpty());
        assertTrue(config.chatModel().includeStopSequence().isEmpty());
        assertEquals(Duration.ofSeconds(10), config.iam().timeout());
        assertEquals("urn:ibm:params:oauth:grant-type:apikey", config.iam().grantType());
        assertEquals(WireMockUtil.DEFAULT_EMBEDDING_MODEL, config.embeddingModel().modelId());

        String modelId = config.chatModel().modelId();
        String projectId = config.projectId();
        String input = "TEST";
        Parameters parameters = Parameters.builder()
                .decodingMethod(config.chatModel().decodingMethod())
                .temperature(config.chatModel().temperature())
                .minNewTokens(config.chatModel().minNewTokens())
                .maxNewTokens(config.chatModel().maxNewTokens())
                .build();

        TextGenerationRequest body = new TextGenerationRequest(modelId, projectId, input + "\n", parameters);

        mockServers.mockIAMBuilder(200)
                .response("token", new Date())
                .build();

        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_CHAT_API, 200)
                .token("token")
                .body(mapper.writeValueAsString(body))
                .response("""
                            {
                                "model_id": "meta-llama/llama-2-70b-chat",
                                "created_at": "2024-01-21T17:06:14.052Z",
                                "results": [
                                    {
                                        "generated_text": "Response!",
                                        "generated_token_count": 5,
                                        "input_token_count": 50,
                                        "stop_reason": "eos_token",
                                        "seed": 2123876088
                                    }
                                ]
                            }
                        """)
                .build();

        assertEquals("Response!", model.generate(input));
    }
}
