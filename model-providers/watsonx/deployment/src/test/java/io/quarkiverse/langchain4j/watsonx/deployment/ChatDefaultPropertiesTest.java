package io.quarkiverse.langchain4j.watsonx.deployment;

import static com.ibm.watsonx.ai.chat.model.UserMessage.text;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.ibm.watsonx.ai.chat.model.ChatMessage;
import com.ibm.watsonx.ai.chat.model.SystemMessage;
import com.ibm.watsonx.ai.chat.model.TextChatRequest;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.watsonx.WatsonxChatRequestParameters;
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
    }

    @Test
    void check_chat_model_config() throws Exception {

        var messages = List.<ChatMessage> of(
                SystemMessage.of("SystemMessage"),
                text("UserMessage"));

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
                UserMessage.from("UserMessage")).aiMessage().text());

        CapturedRequest request = mock.chat().singleRequest();
        assertWatsonxRequest(request, URL_WATSONX_CHAT_API);
        assertJsonBody(request, body);
    }

    @Test
    void check_chat_streaming_model_config() throws Exception {

        var messagesToSend = List.<ChatMessage> of(
                SystemMessage.of("SystemMessage"),
                text("UserMessage"));

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
                UserMessage.from("UserMessage"));

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

    @ParameterizedTest
    @ValueSource(strings = { "reasoning", "reasoning_content" })
    void reasoning(String field) {
        mock.raw().on("POST", URL_WATSONX_CHAT_API)
                .reply(200, """
                        {"id":"review","model_id":"openai/gpt-oss-120b","created":1,
                         "choices":[{"index":0,"finish_reason":"stop","message":{
                         "role":"assistant","content":"Hello","%s":"Thinking"}}]}
                        """.formatted(field));
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Hello"))
                .parameters(WatsonxChatRequestParameters.builder().thinking(true).build())
                .build();
        ChatResponse response = chatModel.chat(request);
        assertThat(response.aiMessage().text()).isEqualTo("Hello");
        assertThat(response.aiMessage().thinking()).isEqualTo("Thinking");
        assertThat(requests("POST", URL_WATSONX_CHAT_API)).hasSize(1);
        assertWatsonxRequest(requests("POST", URL_WATSONX_CHAT_API).get(0), URL_WATSONX_CHAT_API);
    }

    @ParameterizedTest
    @ValueSource(strings = { "reasoning", "reasoning_content" })
    void streamingReasoning(String field) throws Exception {
        mock.raw().on("POST", URL_WATSONX_CHAT_STREAMING_API)
                .replies(sse(
                        """
                                data: {"id":"review","model_id":"openai/gpt-oss-120b","created":1,"choices":[{"index":0,"delta":{"role":"assistant","%s":"Thinking"}}]}

                                data: {"id":"review","model_id":"openai/gpt-oss-120b","created":1,"choices":[{"index":0,"finish_reason":"stop","delta":{"content":"Hello"}}]}

                                data: [DONE]

                                """
                                .formatted(field)));
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Hello"))
                .parameters(WatsonxChatRequestParameters.builder().thinking(true).build())
                .build();
        CompletableFuture<ChatResponse> result = new CompletableFuture<>();
        List<String> thinking = new ArrayList<>();
        streamingChatModel.chat(request, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
            }

            @Override
            public void onPartialThinking(PartialThinking partialThinking) {
                thinking.add(partialThinking.text());
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                result.complete(response);
            }

            @Override
            public void onError(Throwable error) {
                result.completeExceptionally(error);
            }
        });
        ChatResponse response = result.get(10, TimeUnit.SECONDS);
        assertThat(response.aiMessage().text()).isEqualTo("Hello");
        assertThat(response.aiMessage().thinking()).isEqualTo("Thinking");
        assertThat(thinking).containsExactly("Thinking");
        assertThat(requests("POST", URL_WATSONX_CHAT_STREAMING_API)).hasSize(1);
        assertWatsonxRequest(requests("POST", URL_WATSONX_CHAT_STREAMING_API).get(0), URL_WATSONX_CHAT_STREAMING_API);
    }
}
