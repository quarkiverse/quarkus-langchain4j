package io.quarkiverse.langchain4j.bam.deployment;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

public class AllPropertiesTest {

    static WireMockServer wireMockServer;
    static ObjectMapper mapper;
    static WireMockUtil mockServers;

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.base-url", WireMockUtil.URL)
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.api-key", WireMockUtil.API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.timeout", "60s")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.log-requests", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.log-responses", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.timeout", "60s")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.version", "aaaa-mm-dd")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.chat-model.model-id", "my_super_model")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.chat-model.decoding-method", "greedy")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.chat-model.include-stop-sequence", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.chat-model.max-new-tokens", "200")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.chat-model.min-new-tokens", "10")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.chat-model.random-seed", "2")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.chat-model.stop-sequences", "\n,\n\n")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.chat-model.temperature", "1.5")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.chat-model.time-limit", "1500")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.chat-model.top-k", "90")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.chat-model.top-p", "0.5")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.chat-model.typical-p", "0.5")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.chat-model.repetition-penalty", "2.0")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.chat-model.truncate-input-tokens", "0")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.chat-model.beam-width", "2")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.moderation-model.hap", "0.7")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.moderation-model.social-bias", "0.6")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.moderation-model.messages-to-moderate", "user,system")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.embedding-model.model-id", "my_super_embedding_model")
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

        assertEquals(WireMockUtil.URL, config.baseUrl().get().toString());
        assertEquals(WireMockUtil.API_KEY, config.apiKey());
        assertEquals(Duration.ofSeconds(60), config.timeout().get());
        assertEquals(true, config.logRequests().orElse(false));
        assertEquals(true, config.logResponses().orElse(false));
        assertEquals("aaaa-mm-dd", config.version());
        assertEquals("my_super_model", config.chatModel().modelId());
        assertEquals("greedy", config.chatModel().decodingMethod());
        assertEquals(true, config.chatModel().includeStopSequence().get());
        assertEquals(200, config.chatModel().maxNewTokens());
        assertEquals(10, config.chatModel().minNewTokens());
        assertEquals(2, config.chatModel().randomSeed().get());
        assertEquals(List.of("\n", "\n\n"), config.chatModel().stopSequences().get());
        assertEquals(1.5, config.chatModel().temperature());
        assertEquals(1500, config.chatModel().timeLimit().get());
        assertEquals(90, config.chatModel().topK().get());
        assertEquals(0.5, config.chatModel().topP().get());
        assertEquals(0.5, config.chatModel().typicalP().get());
        assertEquals(2.0, config.chatModel().repetitionPenalty().get());
        assertEquals(0, config.chatModel().truncateInputTokens().get());
        assertEquals(2, config.chatModel().beamWidth().get());
        assertEquals("my_super_embedding_model", config.embeddingModel().modelId());
        assertEquals(List.of(ChatMessageType.USER, ChatMessageType.SYSTEM), config.moderationModel().messagesToModerate());
        assertTrue(config.moderationModel().hap().isPresent());
        assertTrue(config.moderationModel().socialBias().isPresent());
        assertEquals(0.7f, config.moderationModel().hap().get());
        assertEquals(0.6f, config.moderationModel().socialBias().get());

        var modelId = config.chatModel().modelId();

        var parameters = Parameters.builder()
                .minNewTokens(10)
                .maxNewTokens(200)
                .decodingMethod("greedy")
                .includeStopSequence(true)
                .randomSeed(2)
                .stopSequences(List.of("\n", "\n\n"))
                .temperature(1.5)
                .timeLimit(1500)
                .topK(90)
                .topP(0.5)
                .typicalP(0.5)
                .repetitionPenalty(2.0)
                .truncateInputTokens(0)
                .beamWidth(2)
                .build();

        List<Message> messages = List.of(
                new Message("user", "Hello how are you?"));

        var body = new TextGenerationRequest(modelId, messages, parameters);

        mockServers
                .mockBuilder(WireMockUtil.URL_CHAT_API, 200, config.version())
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
