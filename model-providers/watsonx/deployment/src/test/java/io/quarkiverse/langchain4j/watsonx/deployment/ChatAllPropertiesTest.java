package io.quarkiverse.langchain4j.watsonx.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.TokenCountEstimator;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatMessage;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatMessage.TextChatMessageSystem;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatMessage.TextChatMessageUser;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatParameters;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatRequest;
import io.quarkiverse.langchain4j.watsonx.bean.TokenizationRequest;
import io.quarkus.test.QuarkusUnitTest;

public class ChatAllPropertiesTest extends WireMockAbstract {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", WireMockUtil.URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", WireMockUtil.API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.space-id", "my-space-id")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", WireMockUtil.PROJECT_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.timeout", "60s")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.log-requests", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.log-responses", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.version", "aaaa-mm-dd")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", WireMockUtil.URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.timeout", "60s")
            .overrideConfigKey("quarkus.langchain4j.watsonx.mode", "chat")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.frequency-penalty", "2")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.logprobs", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.top-logprobs", "2")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.model-id", "my_super_model")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.max-tokens", "200")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.n", "2")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.presence-penalty", "2")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.seed", "41")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.stop", "word1,word2")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.temperature", "1.5")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.top-p", "0.5")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.response-format", "new_format")
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
    TokenCountEstimator tokenCountEstimator;

    static TextChatParameters parameters = TextChatParameters.builder()
            .frequencyPenalty(2.0)
            .logprobs(true)
            .topLogprobs(2)
            .maxTokens(200)
            .n(2)
            .presencePenalty(2.0)
            .seed(41)
            .stop(List.of("word1", "word2"))
            .temperature(1.5)
            .timeLimit(60000L)
            .topP(0.5)
            .responseFormat("new_format")
            .build();

    @Test
    void check_config() throws Exception {
        var runtimeConfig = langchain4jWatsonConfig.defaultConfig();
        assertEquals(WireMockUtil.URL_WATSONX_SERVER, runtimeConfig.baseUrl().orElse(null).toString());
        assertEquals(WireMockUtil.URL_IAM_SERVER, runtimeConfig.iam().baseUrl().toString());
        assertEquals(WireMockUtil.API_KEY, runtimeConfig.apiKey().orElse(null));
        assertEquals("my-space-id", runtimeConfig.spaceId().orElse(null));
        assertEquals(WireMockUtil.PROJECT_ID, runtimeConfig.projectId().orElse(null));
        assertEquals(Duration.ofSeconds(60), runtimeConfig.timeout().get());
        assertEquals(Duration.ofSeconds(60), runtimeConfig.iam().timeout().get());
        assertEquals(true, runtimeConfig.logRequests().orElse(false));
        assertEquals(true, runtimeConfig.logResponses().orElse(false));
        assertEquals("aaaa-mm-dd", runtimeConfig.version());
        assertEquals("my_super_model", runtimeConfig.chatModel().modelId());
        assertEquals(2.0, runtimeConfig.chatModel().frequencyPenalty());
        assertEquals(true, runtimeConfig.chatModel().logprobs());
        assertEquals(2, runtimeConfig.chatModel().topLogprobs().orElse(null));
        assertEquals(200, runtimeConfig.chatModel().maxTokens());
        assertEquals(2, runtimeConfig.chatModel().n());
        assertEquals(2.0, runtimeConfig.chatModel().presencePenalty());
        assertEquals(41, runtimeConfig.chatModel().seed().orElse(null));
        assertEquals(List.of("word1", "word2"), runtimeConfig.chatModel().stop().orElse(null));
        assertEquals(1.5, runtimeConfig.chatModel().temperature());
        assertEquals(0.5, runtimeConfig.chatModel().topP());
        assertEquals("new_format", runtimeConfig.chatModel().responseFormat().orElse(null));
    }

    @Test
    void check_chat_model_config() throws Exception {
        var config = langchain4jWatsonConfig.defaultConfig();
        String modelId = config.chatModel().modelId();
        String spaceId = config.spaceId().orElse(null);
        String projectId = config.projectId().orElse(null);

        var messages = List.<TextChatMessage> of(
                TextChatMessageSystem.of("SystemMessage"),
                TextChatMessageUser.of("UserMessage"));

        TextChatRequest body = new TextChatRequest(modelId, spaceId, projectId, messages, null, null, parameters);
        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_CHAT_API, 200, "aaaa-mm-dd")
                .body(mapper.writeValueAsString(body))
                .response(WireMockUtil.RESPONSE_WATSONX_CHAT_API)
                .build();

        assertEquals("AI Response", chatModel.generate(dev.langchain4j.data.message.SystemMessage.from("SystemMessage"),
                dev.langchain4j.data.message.UserMessage.from("UserMessage")).content().text());
    }

    @Test
    void check_token_count_estimator() throws Exception {
        var config = langchain4jWatsonConfig.defaultConfig();
        String modelId = config.chatModel().modelId();
        String spaceId = config.spaceId().orElse(null);
        String projectId = config.projectId().orElse(null);

        var body = new TokenizationRequest(modelId, "test", spaceId, projectId);

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
        String spaceId = config.spaceId().orElse(null);
        String projectId = config.projectId().orElse(null);

        var messagesToSend = List.<TextChatMessage> of(
                TextChatMessageSystem.of("SystemMessage"),
                TextChatMessageUser.of("UserMessage"));

        TextChatRequest body = new TextChatRequest(modelId, spaceId, projectId, messagesToSend, null, null, parameters);

        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_CHAT_STREAMING_API, 200, "aaaa-mm-dd")
                .body(mapper.writeValueAsString(body))
                .responseMediaType(MediaType.SERVER_SENT_EVENTS)
                .response(WireMockUtil.RESPONSE_WATSONX_CHAT_STREAMING_API)
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
                .isEqualTo(" Hello");
    }
}
