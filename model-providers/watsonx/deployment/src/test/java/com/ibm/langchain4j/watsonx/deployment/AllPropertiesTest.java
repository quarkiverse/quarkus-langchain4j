package com.ibm.langchain4j.watsonx.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.TokenCountEstimator;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.quarkiverse.langchain4j.watsonx.WatsonxChatModel;
import io.quarkiverse.langchain4j.watsonx.WatsonxStreamingChatModel;
import io.quarkiverse.langchain4j.watsonx.bean.EmbeddingRequest;
import io.quarkiverse.langchain4j.watsonx.bean.Parameters;
import io.quarkiverse.langchain4j.watsonx.bean.Parameters.LengthPenalty;
import io.quarkiverse.langchain4j.watsonx.bean.TextGenerationRequest;
import io.quarkiverse.langchain4j.watsonx.bean.TokenizationRequest;
import io.quarkiverse.langchain4j.watsonx.prompt.impl.NoopPromptFormatter;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusUnitTest;

public class AllPropertiesTest extends WireMockAbstract {

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
            .overrideConfigKey("quarkus.langchain4j.watsonx.chat-model.model-id", "my_super_model")
            .overrideConfigKey("quarkus.langchain4j.watsonx.chat-model.prompt-formatter", "true")
            .overrideConfigKey("quarkus.langchain4j.watsonx.chat-model.prompt-joiner", "@")
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
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.embedding-model.model-id", "my_super_embedding_model")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WireMockUtil.class));

    @Override
    void handlerBeforeEach() {
        mockServers.mockIAMBuilder(200)
                .grantType(langchain4jWatsonConfig.defaultConfig().iam().grantType())
                .response(WireMockUtil.BEARER_TOKEN, new Date())
                .build();
    }

    @Inject
    ChatLanguageModel chatModel;

    @Inject
    StreamingChatLanguageModel streamingChatModel;

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    TokenCountEstimator tokenCountEstimator;

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
    void prompt_formatter() {
        var unwrapChatModel = (WatsonxChatModel) ClientProxy.unwrap(chatModel);
        assertTrue(unwrapChatModel.getPromptFormatter() instanceof NoopPromptFormatter);

        var unwrapStreamingChatModel = (WatsonxStreamingChatModel) ClientProxy.unwrap(streamingChatModel);
        assertTrue(unwrapStreamingChatModel.getPromptFormatter() instanceof NoopPromptFormatter);
    }

    @Test
    void check_config() throws Exception {
        var runtimeConfig = langchain4jWatsonConfig.defaultConfig();
        var fixedRuntimeConfig = langchain4jWatsonFixedRuntimeConfig.defaultConfig();
        assertEquals(WireMockUtil.URL_WATSONX_SERVER, runtimeConfig.baseUrl().toString());
        assertEquals(WireMockUtil.URL_IAM_SERVER, runtimeConfig.iam().baseUrl().toString());
        assertEquals(WireMockUtil.API_KEY, runtimeConfig.apiKey());
        assertEquals(WireMockUtil.PROJECT_ID, runtimeConfig.projectId());
        assertEquals(Duration.ofSeconds(60), runtimeConfig.timeout().get());
        assertEquals(Duration.ofSeconds(60), runtimeConfig.iam().timeout().get());
        assertEquals("grantME", runtimeConfig.iam().grantType());
        assertEquals(true, runtimeConfig.logRequests().orElse(false));
        assertEquals(true, runtimeConfig.logResponses().orElse(false));
        assertEquals("aaaa-mm-dd", runtimeConfig.version());
        assertEquals("my_super_model", fixedRuntimeConfig.chatModel().modelId());
        assertEquals("greedy", runtimeConfig.chatModel().decodingMethod());
        assertEquals(1.1, runtimeConfig.chatModel().lengthPenalty().decayFactor().get());
        assertEquals(0, runtimeConfig.chatModel().lengthPenalty().startIndex().get());
        assertEquals(200, runtimeConfig.chatModel().maxNewTokens());
        assertEquals(10, runtimeConfig.chatModel().minNewTokens());
        assertEquals(2, runtimeConfig.chatModel().randomSeed().get());
        assertEquals(List.of("\n", "\n\n"), runtimeConfig.chatModel().stopSequences().get());
        assertEquals(1.5, runtimeConfig.chatModel().temperature());
        assertEquals(90, runtimeConfig.chatModel().topK().get());
        assertEquals(0.5, runtimeConfig.chatModel().topP().get());
        assertEquals(2.0, runtimeConfig.chatModel().repetitionPenalty().get());
        assertEquals(0, runtimeConfig.chatModel().truncateInputTokens().get());
        assertEquals(false, runtimeConfig.chatModel().includeStopSequence().get());
        assertEquals("@", runtimeConfig.chatModel().promptJoiner());
        assertEquals(true, fixedRuntimeConfig.chatModel().promptFormatter());
        assertEquals("my_super_embedding_model", runtimeConfig.embeddingModel().modelId());
    }

    @Test
    void check_chat_model_config() throws Exception {
        var config = langchain4jWatsonConfig.defaultConfig();
        String modelId = langchain4jWatsonFixedRuntimeConfig.defaultConfig().chatModel().modelId();
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
        String modelId = langchain4jWatsonFixedRuntimeConfig.defaultConfig().chatModel().modelId();
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
        String modelId = langchain4jWatsonFixedRuntimeConfig.defaultConfig().chatModel().modelId();
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
