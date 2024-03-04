package com.ibm.langchain4j.watsonx.deployment;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.Date;
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

import dev.langchain4j.model.chat.ChatLanguageModel;
import io.quarkiverse.langchain4j.watsonx.bean.Parameters;
import io.quarkiverse.langchain4j.watsonx.bean.TextGenerationRequest;
import io.quarkiverse.langchain4j.watsonx.client.WatsonxRestApi;
import io.quarkiverse.langchain4j.watsonx.runtime.config.LangChain4jWatsonxConfig;
import io.quarkus.test.QuarkusUnitTest;

public class AllPropertiesTest {

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
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", WireMockUtil.API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", WireMockUtil.PROJECT_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.timeout", "60s")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.log-requests", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.log-responses", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.version", "aaaa-mm-dd")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", WireMockUtil.URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.timeout", "60s")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.grant-type", "grantME")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.model-id", "my_super_model")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.decoding-method", "greedy")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.max-new-tokens", "200")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.min-new-tokens", "10")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.random-seed", "2")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.stop-sequences", "\n,\n\n")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.temperature", "1.5")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.top-k", "90")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.top-p", "0.5")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.repetition-penalty", "2.0")
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
        assertEquals(WireMockUtil.URL_WATSONX_SERVER, config.baseUrl().toString());
        assertEquals(WireMockUtil.URL_IAM_SERVER, config.iam().baseUrl().toString());
        assertEquals(WireMockUtil.API_KEY, config.apiKey());
        assertEquals(WireMockUtil.PROJECT_ID, config.projectId());
        assertEquals(Duration.ofSeconds(60), config.timeout());
        assertEquals(Duration.ofSeconds(60), config.iam().timeout());
        assertEquals("grantME", config.iam().grantType());
        assertEquals(true, config.logRequests());
        assertEquals(true, config.logResponses());
        assertEquals("aaaa-mm-dd", config.version());
        assertEquals("my_super_model", config.chatModel().modelId());
        assertEquals("greedy", config.chatModel().decodingMethod());
        assertEquals(200, config.chatModel().maxNewTokens());
        assertEquals(10, config.chatModel().minNewTokens());
        assertEquals(2, config.chatModel().randomSeed().get());
        assertEquals(List.of("\n", "\n\n"), config.chatModel().stopSequences().get());
        assertEquals(1.5, config.chatModel().temperature());
        assertEquals(90, config.chatModel().topK().get());
        assertEquals(0.5, config.chatModel().topP().get());
        assertEquals(2.0, config.chatModel().repetitionPenalty().get());

        String modelId = config.chatModel().modelId();
        String projectId = config.projectId();
        String input = "TEST";
        var parameters = Parameters.builder()
                .minNewTokens(10)
                .maxNewTokens(200)
                .decodingMethod("greedy")
                .randomSeed(2)
                .stopSequences(List.of("\n", "\n\n"))
                .temperature(1.5)
                .topK(90)
                .topP(0.5)
                .repetitionPenalty(2.0)
                .build();

        TextGenerationRequest body = new TextGenerationRequest(modelId, projectId, input + "\n", parameters);

        mockServers.mockIAMBuilder(200)
                .grantType(config.iam().grantType())
                .response(WireMockUtil.BEARER_TOKEN, new Date())
                .build();

        mockServers.mockWatsonBuilder(200, "aaaa-mm-dd")
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
