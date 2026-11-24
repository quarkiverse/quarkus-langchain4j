package io.quarkiverse.langchain4j.ollama.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatRequestOptions;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * The invocation id and the listener attributes carried by {@link ChatRequestOptions} have to reach the
 * {@link ChatModelListener}s - that is the plumbing langchain4j's default {@code chat(...)} methods provide around
 * {@code doChat(...)}, and a model that notifies its listeners by hand bypasses it.
 */
public class OllamaStreamingListenerInvocationTest {

    private static final String CHAT_MODEL_ID = "llama3.2";

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.devservices.enabled", "false")
            .overrideConfigKey("quarkus.langchain4j.ollama.chat-model.model-id", CHAT_MODEL_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.ollama.chat-model.temperature", "0.3");

    @Inject
    StreamingChatModel streamingChatModel;

    @Inject
    RecordingListener listener;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void setUp() {
        mock.reset();
        listener.requests.clear();
        listener.responses.clear();
        listener.errors.clear();
    }

    @Test
    void listenerSeesInvocationIdAndAttributes() throws Exception {
        mock.chat().reply(ChatReply.text("Hi").usage(1, 1));
        UUID invocationId = UUID.randomUUID();

        ChatResponse response = stream(request(), options(invocationId)).get(30, TimeUnit.SECONDS);

        assertThat(response.aiMessage().text()).isEqualTo("Hi");
        assertThat(mock.requests()).hasSize(1);

        assertThat(listener.requests).singleElement().satisfies(context -> {
            assertThat(context.invocationId()).isEqualTo(invocationId);
            assertThat(context.attributes()).containsEntry("tenant", "acme");
            assertThat(context.modelProvider()).isEqualTo(ModelProvider.OLLAMA);
            assertThat(context.chatRequest().parameters().modelName()).isEqualTo(CHAT_MODEL_ID);
            assertThat(context.chatRequest().parameters().temperature()).isEqualTo(0.3);
        });
        assertThat(listener.responses).singleElement().satisfies(context -> {
            assertThat(context.invocationId()).isEqualTo(invocationId);
            assertThat(context.attributes()).containsEntry("tenant", "acme");
            assertThat(context.chatResponse().aiMessage().text()).isEqualTo("Hi");
            assertThat(context.chatResponse().metadata().modelName()).isEqualTo(CHAT_MODEL_ID);
        });
        assertThat(listener.errors).isEmpty();
    }

    /**
     * The model used to notify its listeners by hand, and only on the text path - a response carrying tool calls
     * completed the stream without any {@code onResponse}, so every tool-calling round-trip was a request with no
     * response as far as the listeners were concerned.
     */
    @Test
    void listenerSeesResponseWhenModelRequestsTools() throws Exception {
        mock.chat().reply(ChatReply.toolCall("getWeather", Map.of("city", "Paris")).usage(1, 1));
        UUID invocationId = UUID.randomUUID();
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("weather?"))
                .toolSpecifications(ToolSpecification.builder()
                        .name("getWeather")
                        .parameters(JsonObjectSchema.builder().addStringProperty("city").build())
                        .build())
                .build();

        ChatResponse response = stream(request, options(invocationId)).get(30, TimeUnit.SECONDS);

        assertThat(response.aiMessage().hasToolExecutionRequests()).isTrue();
        assertThat(mock.requests()).hasSize(1);

        assertThat(listener.requests).singleElement().satisfies(context -> {
            assertThat(context.invocationId()).isEqualTo(invocationId);
            assertThat(context.chatRequest().toolSpecifications()).singleElement()
                    .extracting(ToolSpecification::name).isEqualTo("getWeather");
        });
        assertThat(listener.responses).singleElement().satisfies(context -> {
            assertThat(context.invocationId()).isEqualTo(invocationId);
            assertThat(context.attributes()).containsEntry("tenant", "acme");
            assertThat(context.chatResponse().aiMessage().toolExecutionRequests()).singleElement()
                    .satisfies(toolExecutionRequest -> {
                        assertThat(toolExecutionRequest.name()).isEqualTo("getWeather");
                        assertThat(toolExecutionRequest.arguments()).contains("Paris");
                    });
        });
        assertThat(listener.errors).isEmpty();
    }

    /**
     * langchain4j 1.24.0's default streaming {@code chat(...)} calls {@code onRequest} and then {@code doChat} without
     * guarding it, so a request that fails to map must be reported through the handler, or the listeners end up with
     * an {@code onRequest} and no terminal callback.
     */
    @Test
    void listenerSeesErrorWhenStreamingRequestCannotBeMapped() {
        UUID invocationId = UUID.randomUUID();
        // the Ollama message mapper accepts exactly one text alongside images
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from(TextContent.from("one"), TextContent.from("two"),
                        ImageContent.from("https://example.com/image.png")))
                .build();

        CompletableFuture<ChatResponse> response = stream(request, options(invocationId));

        assertThatThrownBy(() -> response.get(30, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .rootCause()
                .isInstanceOf(RuntimeException.class)
                .hasMessageStartingWith("Expecting single text content");
        assertThat(mock.requests()).isEmpty();

        assertThat(listener.requests).singleElement().satisfies(context -> {
            assertThat(context.invocationId()).isEqualTo(invocationId);
        });
        assertThat(listener.responses).isEmpty();
        assertThat(listener.errors).singleElement().satisfies(context -> {
            assertThat(context.invocationId()).isEqualTo(invocationId);
            assertThat(context.attributes()).containsEntry("tenant", "acme");
            assertThat(context.error()).hasMessageStartingWith("Expecting single text content");
        });
    }

    private static ChatRequest request() {
        return ChatRequest.builder().messages(UserMessage.from("hello")).build();
    }

    private static ChatRequestOptions options(UUID invocationId) {
        return ChatRequestOptions.builder()
                .invocationId(invocationId)
                .addListenerAttribute("tenant", "acme")
                .build();
    }

    private CompletableFuture<ChatResponse> stream(ChatRequest request, ChatRequestOptions options) {
        CompletableFuture<ChatResponse> response = new CompletableFuture<>();
        streamingChatModel.chat(request, options, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                response.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                response.completeExceptionally(error);
            }
        });
        return response;
    }

    @Singleton
    public static class RecordingListener implements ChatModelListener {

        final List<ChatModelRequestContext> requests = new CopyOnWriteArrayList<>();
        final List<ChatModelResponseContext> responses = new CopyOnWriteArrayList<>();
        final List<ChatModelErrorContext> errors = new CopyOnWriteArrayList<>();

        @Override
        public void onRequest(ChatModelRequestContext requestContext) {
            requests.add(requestContext);
        }

        @Override
        public void onResponse(ChatModelResponseContext responseContext) {
            responses.add(responseContext);
        }

        @Override
        public void onError(ChatModelErrorContext errorContext) {
            errors.add(errorContext);
        }
    }
}
