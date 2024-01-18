package com.ibm.generativeai.bam.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.MediaType;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.bam.BamRestApi;
import io.quarkiverse.langchain4j.bam.Message;
import io.quarkiverse.langchain4j.bam.Parameters;
import io.quarkiverse.langchain4j.bam.TextGenerationRequest;
import io.quarkiverse.langchain4j.bam.runtime.config.Langchain4jBamConfig;
import io.quarkus.test.QuarkusUnitTest;

public class AiServiceTest {

    static WireMockServer wireMockServer;
    static ObjectMapper mapper;

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.base-url", Util.URL)
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.api-key", Util.API_KEY)
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(Util.class));

    @BeforeAll
    static void beforeAll() {
        wireMockServer = new WireMockServer(options().port(Util.PORT));
        wireMockServer.start();
        mapper = BamRestApi.objectMapper(new ObjectMapper());
    }

    @AfterAll
    static void afterAll() {
        wireMockServer.stop();
    }

    @RegisterAiService
    @Singleton
    interface NewAIService {

        @SystemMessage("This is a systemMessage")
        @UserMessage("This is a userMessage {text}")
        String chat(String text);
    }

    @Inject
    NewAIService service;

    @Inject
    Langchain4jBamConfig config;

    @Test
    void chat() throws Exception {

        var modelId = config.chatModel().modelId();

        var parameters = Parameters.builder()
                .decodingMethod(config.chatModel().decodingMethod())
                .temperature(config.chatModel().temperature())
                .minNewTokens(config.chatModel().minNewTokens())
                .maxNewTokens(config.chatModel().maxNewTokens())
                .build();

        List<Message> messages = List.of(
                new Message("system", "This is a systemMessage"),
                new Message("user", "This is a userMessage Hello"));

        var body = new TextGenerationRequest(modelId, messages, parameters);

        wireMockServer.stubFor(
                post(urlEqualTo(Util.URL_CHAT_API.formatted(config.version())))
                        .withHeader("Authorization", equalTo("Bearer %s".formatted(Util.API_KEY)))
                        .withRequestBody(equalToJson(mapper.writeValueAsString(body)))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", MediaType.APPLICATION_JSON)
                                        .withStatus(200)
                                        .withBody("""
                                                {
                                                    "results": [
                                                        {
                                                            "generated_token_count": 20,
                                                            "input_token_count": 146,
                                                            "stop_reason": "max_tokens",
                                                            "seed": 40268626,
                                                            "generated_text": "AI Response"
                                                        }
                                                    ]
                                                }
                                                """)));

        assertEquals("AI Response", service.chat("Hello"));
    }
}
