package com.ibm.langchain4j.watsonx.deployment;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.TokenCountEstimator;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.quarkiverse.langchain4j.watsonx.bean.EmbeddingRequest;
import io.quarkiverse.langchain4j.watsonx.bean.Parameters;
import io.quarkiverse.langchain4j.watsonx.bean.Parameters.LengthPenalty;
import io.quarkiverse.langchain4j.watsonx.bean.TextGenerationRequest;
import io.quarkiverse.langchain4j.watsonx.bean.TokenizationRequest;
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
    ChatLanguageModel chatModel;

    @Inject
    StreamingChatLanguageModel streamingChatModel;

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    TokenCountEstimator tokenCountEstimator;

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
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.length-penalty.decay-factor", "1.1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.length-penalty.start-index", "0")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.max-new-tokens", "200")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.min-new-tokens", "10")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.random-seed", "2")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.stop-sequences", "\n,\n\n")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.temperature", "1.5")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.top-k", "90")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.top-p", "0.5")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.repetition-penalty", "2.0")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.truncate-input-tokens", "0")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.include-stop-sequence", "false")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.prompt-joiner", "@")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.embedding-model.model-id", "my_super_embedding_model")
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

    @BeforeEach
    void beforeEach() {
        watsonxServer.resetAll();
        iamServer.resetAll();
        mockServers.mockIAMBuilder(200)
                .grantType(langchain4jWatsonConfig.defaultConfig().iam().grantType())
                .response(WireMockUtil.BEARER_TOKEN, new Date())
                .build();
    }

    @AfterAll
    static void afterAll() {
        watsonxServer.stop();
        iamServer.stop();
    }

    static Parameters parameters = Parameters.builder()
            .minNewTokens(10)
            .maxNewTokens(200)
            .decodingMethod("greedy")
            .lengthPenalty(new LengthPenalty(1.1, 0))
            .randomSeed(2)
            .stopSequences(List.of("\n", "\n\n"))
            .temperature(1.5)
            .topK(90)
            .topP(0.5)
            .repetitionPenalty(2.0)
            .truncateInputTokens(0)
            .includeStopSequence(false)
            .build();

    @Test
    void check_config() throws Exception {
        var config = langchain4jWatsonConfig.defaultConfig();
        assertEquals(WireMockUtil.URL_WATSONX_SERVER, config.baseUrl().toString());
        assertEquals(WireMockUtil.URL_IAM_SERVER, config.iam().baseUrl().toString());
        assertEquals(WireMockUtil.API_KEY, config.apiKey());
        assertEquals(WireMockUtil.PROJECT_ID, config.projectId());
        assertEquals(Duration.ofSeconds(60), config.timeout().get());
        assertEquals(Duration.ofSeconds(60), config.iam().timeout().get());
        assertEquals("grantME", config.iam().grantType());
        assertEquals(true, config.logRequests().orElse(false));
        assertEquals(true, config.logResponses().orElse(false));
        assertEquals("aaaa-mm-dd", config.version());
        assertEquals("my_super_model", config.chatModel().modelId());
        assertEquals("greedy", config.chatModel().decodingMethod());
        assertEquals(1.1, config.chatModel().lengthPenalty().get().decayFactor().get());
        assertEquals(0, config.chatModel().lengthPenalty().get().startIndex().get());
        assertEquals(200, config.chatModel().maxNewTokens());
        assertEquals(10, config.chatModel().minNewTokens());
        assertEquals(2, config.chatModel().randomSeed().get());
        assertEquals(List.of("\n", "\n\n"), config.chatModel().stopSequences().get());
        assertEquals(1.5, config.chatModel().temperature());
        assertEquals(90, config.chatModel().topK().get());
        assertEquals(0.5, config.chatModel().topP().get());
        assertEquals(2.0, config.chatModel().repetitionPenalty().get());
        assertEquals(0, config.chatModel().truncateInputTokens().get());
        assertEquals(false, config.chatModel().includeStopSequence().get());
        assertEquals("@", config.chatModel().promptJoiner().get());
        assertEquals("my_super_embedding_model", config.embeddingModel().modelId());
    }

    @Test
    void check_chat_model_config() throws Exception {
        var config = langchain4jWatsonConfig.defaultConfig();
        String modelId = config.chatModel().modelId();
        String projectId = config.projectId();

        TextGenerationRequest body = new TextGenerationRequest(modelId, projectId, "SystemMessage@UserMessage", parameters);
        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_CHAT_API, 200, "aaaa-mm-dd")
                .body(mapper.writeValueAsString(body))
                .response(WireMockUtil.RESPONSE_WATSONX_CHAT_API)
                .build();

        assertEquals("AI Response", chatModel.generate(dev.langchain4j.data.message.SystemMessage.from("SystemMessage"),
                dev.langchain4j.data.message.UserMessage.from("UserMessage")).content().text());
    }

    @Test
    void check_embedding_model() throws Exception {
        var config = langchain4jWatsonConfig.defaultConfig();
        String modelId = config.embeddingModel().modelId();
        String projectId = config.projectId();

        EmbeddingRequest request = new EmbeddingRequest(modelId, projectId,
                List.of("Embedding THIS!"));

        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_EMBEDDING_API, 200, "aaaa-mm-dd")
                .body(mapper.writeValueAsString(request))
                .response(WireMockUtil.RESPONSE_WATSONX_EMBEDDING_API.formatted(modelId))
                .build();

        Response<Embedding> response = embeddingModel.embed("Embedding THIS!");
        assertNotNull(response);
        assertNotNull(response.content());
    }

    @Test
    void check_token_count_estimator() throws Exception {
        var config = langchain4jWatsonConfig.defaultConfig();
        String modelId = config.chatModel().modelId();
        String projectId = config.projectId();

        var body = new TokenizationRequest(modelId, "test", projectId);

        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_TOKENIZER_API, 200, "aaaa-mm-dd")
                .body(mapper.writeValueAsString(body))
                .response(WireMockUtil.RESPONSE_WATSONX_TOKENIZER_API.formatted(modelId))
                .build();

        assertEquals(11, tokenCountEstimator.estimateTokenCount("test"));
    }

    @Test
    void check_chat_streaming_model_config() throws Exception {
        var config = langchain4jWatsonConfig.defaultConfig();
        String modelId = config.chatModel().modelId();
        String projectId = config.projectId();

        TextGenerationRequest body = new TextGenerationRequest(modelId, projectId, "SystemMessage@UserMessage", parameters);

        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_CHAT_STREAMING_API, 200, "aaaa-mm-dd")
                .body(mapper.writeValueAsString(body))
                .responseMediaType(MediaType.SERVER_SENT_EVENTS)
                .response(WireMockUtil.RESPONSE_WATSONX_STREAMING_API)
                .build();

        var messages = List.of(
                dev.langchain4j.data.message.SystemMessage.from("SystemMessage"),
                dev.langchain4j.data.message.UserMessage.from("UserMessage"));

        var streamingResponse = new AtomicReference<AiMessage>();
        streamingChatModel.generate(messages, WireMockUtil.streamingResponseHandler(streamingResponse));

        await().atMost(Duration.ofMinutes(1))
                .pollInterval(Duration.ofSeconds(2))
                .until(() -> streamingResponse.get() != null);

        assertThat(streamingResponse.get().text())
                .isNotNull()
                .isEqualTo(". I'm a beginner");
    }
}
