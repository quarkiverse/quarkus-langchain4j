package com.ibm.generativeai.bam.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;

import io.quarkiverse.langchain4j.bam.BamChatModel;
import io.quarkiverse.langchain4j.bam.BamRestApi;
import io.quarkiverse.langchain4j.bam.Message;
import io.quarkiverse.langchain4j.bam.Parameters;
import io.quarkiverse.langchain4j.bam.TextGenerationRequest;
import io.quarkiverse.langchain4j.bam.runtime.BamRecorder;
import io.quarkiverse.langchain4j.bam.runtime.config.Langchain4jBamConfig;
import io.quarkus.test.QuarkusUnitTest;

public class DefaultPropertiesTest {

    static WireMockServer wireMockServer;
    static ObjectMapper mapper;

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.base-url", Util.URL)
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.api-key", Util.API_KEY)
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(Util.class));

    @Inject
    Langchain4jBamConfig config;

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

    @Test
    void generate() throws Exception {

        assertEquals(Duration.ofSeconds(10), config.timeout());
        assertEquals("2024-01-10", config.version());
        assertEquals(false, config.logRequests());
        assertEquals(false, config.logResponses());
        assertEquals("meta-llama/llama-2-70b-chat", config.chatModel().modelId());
        assertEquals("greedy", config.chatModel().decodingMethod());
        assertEquals(1.0, config.chatModel().temperature());
        assertEquals(0, config.chatModel().minNewTokens());
        assertEquals(200, config.chatModel().maxNewTokens());
        assertEquals(1.0, config.chatModel().temperature());

        var modelId = config.chatModel().modelId();

        var parameters = Parameters.builder()
                .decodingMethod(config.chatModel().decodingMethod())
                .temperature(config.chatModel().temperature())
                .minNewTokens(config.chatModel().minNewTokens())
                .maxNewTokens(config.chatModel().maxNewTokens())
                .build();

        List<Message> messages = List.of(
                new Message("user", "Hello how are you?"));

        var body = new TextGenerationRequest(modelId, messages, parameters);

        wireMockServer.stubFor(
                post(urlEqualTo(Util.URL_CHAT_API.formatted(config.version())))
                        .withHeader("Authorization", equalTo("Bearer %s".formatted(Util.API_KEY)))
                        .withRequestBody(equalToJson(mapper.writeValueAsString(body)))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", MediaType.APPLICATION_JSON)
                                        .withBody(
                                                """
                                                        {
                                                            "id": "05a245ad-1da7-4b9d-9807-ae1733177c1d",
                                                            "model_id": "meta-llama/llama-2-70b-chat",
                                                            "created_at": "2023-09-01T09:28:29.378Z",
                                                            "results": [
                                                                {
                                                                    "generated_token_count": 20,
                                                                    "input_token_count": 146,
                                                                    "stop_reason": "max_tokens",
                                                                    "seed": 40268626,
                                                                    "generated_text": "Hello! I'm doing well, thanks for asking. I'm here to assist you"
                                                                }
                                                            ],
                                                            "conversation_id": "cd3a9bca-b88e-41e4-9d62-bab33098fe39"
                                                        }
                                                        """)));

        BamRecorder recorder = new BamRecorder();
        BamChatModel model = (BamChatModel) recorder.chatModel(config).get();
        assertEquals("Hello! I'm doing well, thanks for asking. I'm here to assist you", model.generate("Hello how are you?"));
    }
}
