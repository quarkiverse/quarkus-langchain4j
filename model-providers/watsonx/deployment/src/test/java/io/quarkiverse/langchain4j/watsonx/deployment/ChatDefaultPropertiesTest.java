package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.DEFAULT_CHAT_MODEL;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.DEFAULT_TIME_LIMIT;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.PROJECT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.URL_WATSONX_CHAT_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.URL_WATSONX_CHAT_STREAMING_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.streamingChatResponseHandler;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

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
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkus.test.QuarkusExtensionTest;

public class ChatDefaultPropertiesTest extends WatsonxMockTestBase {

    @RegisterExtension
    static QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WatsonxTestUtil.class));

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
        assertEquals(Optional.empty(), runtimeConfig.chatModel().frequencyPenalty());
        assertEquals(Optional.empty(), runtimeConfig.chatModel().logprobs());
        assertTrue(runtimeConfig.chatModel().topLogprobs().isEmpty());
        assertEquals(1024, runtimeConfig.chatModel().maxOutputTokens());
        assertEquals(Optional.empty(), runtimeConfig.chatModel().presencePenalty());
        assertEquals(Optional.empty(), runtimeConfig.chatModel().seed());
        assertEquals(Optional.empty(), runtimeConfig.chatModel().stop());
        assertEquals(1.0, runtimeConfig.chatModel().temperature());
        assertEquals(Optional.empty(), runtimeConfig.chatModel().topP());
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
                .maxCompletionTokens(1024)
                .temperature(1.0)
                .timeLimit(DEFAULT_TIME_LIMIT.toMillis())
                .build();

        mock.chat().reply(ChatReply.text("AI Response").usage(59, 47));

        assertEquals("AI Response", chatModel.chat(dev.langchain4j.data.message.SystemMessage.from("SystemMessage"),
                dev.langchain4j.data.message.UserMessage.from("UserMessage")).aiMessage().text());

        CapturedRequest request = mock.chat().singleRequest();
        assertWatsonxRequest(request, URL_WATSONX_CHAT_API);
        assertJsonBody(request, body);
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
                .maxCompletionTokens(1024)
                .temperature(1.0)
                .timeLimit(DEFAULT_TIME_LIMIT.toMillis())
                .build();

        mock.chat().reply(ChatReply.text("Hello").usage(38, 3));

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

        CapturedRequest request = mock.chat().singleRequest();
        assertWatsonxRequest(request, URL_WATSONX_CHAT_STREAMING_API);
        assertJsonBody(request, body);
    }
}
