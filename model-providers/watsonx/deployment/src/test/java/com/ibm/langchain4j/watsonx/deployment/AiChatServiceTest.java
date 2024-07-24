package com.ibm.langchain4j.watsonx.deployment;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Date;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.watsonx.bean.Parameters;
import io.quarkiverse.langchain4j.watsonx.bean.TextGenerationRequest;
import io.quarkiverse.langchain4j.watsonx.client.WatsonxRestApi;
import io.quarkiverse.langchain4j.watsonx.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.LangChain4jWatsonxConfig;
import io.quarkus.test.QuarkusUnitTest;

public class AiChatServiceTest {

    static WireMockServer watsonxServer;
    static WireMockServer iamServer;
    static ObjectMapper mapper;

    @Inject
    LangChain4jWatsonxConfig langchain4jWatsonConfig;

    @Inject
    ChatLanguageModel chatModel;

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

    @BeforeEach
    void beforeEach() {
        watsonxServer.resetAll();
        iamServer.resetAll();
    }

    @RegisterAiService
    @Singleton
    interface NewAIService {

        @SystemMessage("This is a systemMessage\n")
        @UserMessage("This is a userMessage {text}")
        String chat(String text);
    }

    @Inject
    NewAIService service;

    @Test
    void chat() throws Exception {

        LangChain4jWatsonxConfig.WatsonConfig watsonConfig = langchain4jWatsonConfig.defaultConfig();
        ChatModelConfig chatModelConfig = watsonConfig.chatModel();
        String modelId = chatModelConfig.modelId();
        String projectId = watsonConfig.projectId();
        String input = new StringBuilder()
                .append("This is a systemMessage")
                .append("\n")
                .append("This is a userMessage Hello")
                .toString();
        Parameters parameters = Parameters.builder()
                .decodingMethod(chatModelConfig.decodingMethod())
                .temperature(chatModelConfig.temperature())
                .minNewTokens(chatModelConfig.minNewTokens())
                .maxNewTokens(chatModelConfig.maxNewTokens())
                .build();

        TextGenerationRequest body = new TextGenerationRequest(modelId, projectId, input, parameters);

        mockServers.mockIAMBuilder(200)
                .response(WireMockUtil.BEARER_TOKEN, new Date())
                .build();

        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_CHAT_API, 200)
                .body(mapper.writeValueAsString(body))
                .response(WireMockUtil.RESPONSE_WATSONX_CHAT_API)
                .build();

        assertEquals("AI Response", service.chat("Hello"));
    }
}
