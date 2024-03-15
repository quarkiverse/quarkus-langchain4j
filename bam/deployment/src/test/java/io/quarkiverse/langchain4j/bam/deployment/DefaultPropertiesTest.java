package io.quarkiverse.langchain4j.bam.deployment;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Duration;
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

import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.model.chat.ChatLanguageModel;
import io.quarkiverse.langchain4j.bam.BamRestApi;
import io.quarkiverse.langchain4j.bam.Message;
import io.quarkiverse.langchain4j.bam.Parameters;
import io.quarkiverse.langchain4j.bam.TextGenerationRequest;
import io.quarkiverse.langchain4j.bam.runtime.config.LangChain4jBamConfig;
import io.quarkus.test.QuarkusUnitTest;

public class DefaultPropertiesTest {

    static WireMockServer wireMockServer;
    static ObjectMapper mapper;
    static WireMockUtil mockServers;

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.base-url", WireMockUtil.URL)
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.api-key", WireMockUtil.API_KEY)
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WireMockUtil.class));

    @Inject
    LangChain4jBamConfig langchain4jBamConfig;

    @Inject
    ChatLanguageModel model;

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

    @Test
    void generate() throws Exception {
        var config = langchain4jBamConfig.defaultConfig();

        assertEquals(Duration.ofSeconds(10), config.timeout());
        assertEquals(WireMockUtil.VERSION, config.version());
        assertEquals(false, config.logRequests());
        assertEquals(false, config.logResponses());
        assertEquals("ibm/granite-13b-chat-v2", config.chatModel().modelId());
        assertEquals("greedy", config.chatModel().decodingMethod());
        assertEquals(1.0, config.chatModel().temperature());
        assertEquals(0, config.chatModel().minNewTokens());
        assertEquals(200, config.chatModel().maxNewTokens());
        assertEquals(1.0, config.chatModel().temperature());
        assertEquals("ibm/slate.125m.english.rtrvr", config.embeddingModel().modelId());
        assertNotNull(config.moderationModel());
        assertEquals(List.of(ChatMessageType.USER), config.moderationModel().messagesToModerate());
        assertFalse(config.moderationModel().implicitHate().isPresent());
        assertFalse(config.moderationModel().hap().isPresent());
        assertFalse(config.moderationModel().stigma().isPresent());

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

        mockServers
                .mockBuilder(WireMockUtil.URL_CHAT_API, 200)
                .body(mapper.writeValueAsString(body))
                .response("""
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
                        """)
                .build();

        assertEquals("Hello! I'm doing well, thanks for asking. I'm here to assist you", model.generate("Hello how are you?"));
    }
}
