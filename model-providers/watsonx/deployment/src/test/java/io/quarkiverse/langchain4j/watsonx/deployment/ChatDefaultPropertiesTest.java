package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.DEFAULT_CHAT_MODEL;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.DEFAULT_TIME_LIMIT;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.PROJECT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.RESPONSE_WATSONX_CHAT_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.RESPONSE_WATSONX_CHAT_STREAMING_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_IAM_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_CHAT_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_CHAT_STREAMING_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.streamingChatResponseHandler;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.ibm.watsonx.ai.chat.model.ChatMessage;
import com.ibm.watsonx.ai.chat.model.SystemMessage;
import com.ibm.watsonx.ai.chat.model.TextChatRequest;
import com.ibm.watsonx.ai.chat.model.UserMessage;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.quarkus.test.QuarkusUnitTest;

public class ChatDefaultPropertiesTest extends WireMockAbstract {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WireMockUtil.class));

    @Override
    void handlerBeforeEach() {
        mockIAMBuilder(200)
                .response("my_super_token", new Date())
                .build();
    }

    @Inject
    ChatModel chatModel;

    @Inject
    StreamingChatModel streamingChatModel;

    @Test
    void check_config() throws Exception {
        var runtimeConfig = langchain4jWatsonConfig.defaultConfig();
        assertEquals(Optional.of(Duration.ofSeconds(60)), runtimeConfig.timeout());
        assertEquals(Optional.empty(), runtimeConfig.iam().timeout());
        assertEquals(false, runtimeConfig.logRequests().orElse(false));
        assertEquals(false, runtimeConfig.logResponses().orElse(false));
        assertEquals(false, runtimeConfig.logRequestsCurl().orElse(false));
        assertTrue(runtimeConfig.version().isEmpty());
        assertEquals(DEFAULT_CHAT_MODEL, runtimeConfig.chatModel().modelName());
        assertEquals(0, runtimeConfig.chatModel().frequencyPenalty());
        assertEquals(false, runtimeConfig.chatModel().logprobs());
        assertTrue(runtimeConfig.chatModel().topLogprobs().isEmpty());
        assertEquals(1024, runtimeConfig.chatModel().maxOutputTokens());
        assertEquals(0, runtimeConfig.chatModel().presencePenalty());
        assertEquals(Optional.empty(), runtimeConfig.chatModel().seed());
        assertEquals(Optional.empty(), runtimeConfig.chatModel().stop());
        assertEquals(1.0, runtimeConfig.chatModel().temperature());
        assertEquals(1.0, runtimeConfig.chatModel().topP());
        assertEquals(Optional.empty(), runtimeConfig.chatModel().toolChoice());
        assertNull(runtimeConfig.iam().grantType().orElse(null));
        assertEquals(false, langchain4jWatsonConfig.builtInTool().logRequests().orElse(false));
        assertEquals(false, langchain4jWatsonConfig.builtInTool().logResponses().orElse(false));
        assertEquals(false, langchain4jWatsonConfig.builtInTool().logRequestsCurl().orElse(false));
        assertNull(runtimeConfig.chatModel().thinking().orElse(null));
        assertNull(runtimeConfig.chatModel().guidedChoice().orElse(null));
        assertNull(runtimeConfig.chatModel().guidedGrammar().orElse(null));
        assertNull(runtimeConfig.chatModel().guidedRegex().orElse(null));
        assertNull(runtimeConfig.chatModel().repetitionPenalty().orElse(null));
        assertNull(runtimeConfig.chatModel().lengthPenalty().orElse(null));
    }

    @Test
    void check_chat_model_config() throws Exception {

        var messages = List.<ChatMessage> of(
                SystemMessage.of("SystemMessage"),
                UserMessage.text("UserMessage"));

        var body = TextChatRequest.builder()
                .modelId(DEFAULT_CHAT_MODEL)
                .projectId(PROJECT_ID)
                .messages(messages)
                .frequencyPenalty(0.0)
                .logprobs(false)
                .maxCompletionTokens(1024)
                .presencePenalty(0.0)
                .temperature(1.0)
                .topP(1.0)
                .stop(List.of())
                .timeLimit(DEFAULT_TIME_LIMIT.toMillis())
                .build();

        mockWatsonxBuilder(URL_WATSONX_CHAT_API, 200)
                .body(mapper.writeValueAsString(body))
                .response(RESPONSE_WATSONX_CHAT_API)
                .build();

        assertEquals("AI Response", chatModel.chat(dev.langchain4j.data.message.SystemMessage.from("SystemMessage"),
                dev.langchain4j.data.message.UserMessage.from("UserMessage")).aiMessage().text());
    }

    @Test
    void check_chat_streaming_model_config() throws Exception {

        var messagesToSend = List.<ChatMessage> of(
                SystemMessage.of("SystemMessage"),
                UserMessage.text("UserMessage"));

        var body = TextChatRequest.builder()
                .modelId(DEFAULT_CHAT_MODEL)
                .projectId(PROJECT_ID)
                .messages(messagesToSend)
                .frequencyPenalty(0.0)
                .logprobs(false)
                .maxCompletionTokens(1024)
                .presencePenalty(0.0)
                .temperature(1.0)
                .topP(1.0)
                .stop(List.of())
                .timeLimit(DEFAULT_TIME_LIMIT.toMillis())
                .build();

        mockWatsonxBuilder(URL_WATSONX_CHAT_STREAMING_API, 200)
                .body(mapper.writeValueAsString(body))
                .responseMediaType(MediaType.SERVER_SENT_EVENTS)
                .response(RESPONSE_WATSONX_CHAT_STREAMING_API)
                .build();

        var messages = List.of(
                dev.langchain4j.data.message.SystemMessage.from("SystemMessage"),
                dev.langchain4j.data.message.UserMessage.from("UserMessage"));

        var streamingResponse = new AtomicReference<ChatResponse>();
        streamingChatModel.chat(messages, streamingChatResponseHandler(streamingResponse));

        await().atMost(Duration.ofMinutes(1))
                .pollInterval(Duration.ofSeconds(2))
                .until(() -> streamingResponse.get() != null);

        assertThat(streamingResponse.get().aiMessage().text())
                .isNotNull()
                .isEqualTo("Hello");
    }
}
