package io.quarkiverse.langchain4j.azure.openai.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
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

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.ChatRequestOptions;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
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
public class AzureOpenAiListenerInvocationTest {

    private static final String DEPLOYMENT_NAME = "my-gpt4";
    private static final String UNSUPPORTED_TOOL_RESULT_CONTENT = "OpenAI Chat Completions API does not support non-text content in tool results";

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.deployment-name", DEPLOYMENT_NAME)
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.chat-model.temperature", "0.3");

    @Inject
    ChatModel chatModel;

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
    void listenerSeesInvocationIdAndAttributes() {
        mock.chat().reply(ChatReply.text("Hi").usage(1, 1));
        UUID invocationId = UUID.randomUUID();

        ChatResponse response = chatModel.chat(request(), options(invocationId));

        assertThat(response.aiMessage().text()).isEqualTo("Hi");
        assertThat(mock.requests()).hasSize(1);

        assertThat(listener.requests).singleElement().satisfies(context -> {
            assertThat(context.invocationId()).isEqualTo(invocationId);
            assertThat(context.attributes()).containsEntry("tenant", "acme");
            assertThat(context.modelProvider()).isEqualTo(ModelProvider.AZURE_OPEN_AI);
            assertThat(context.chatRequest().parameters().modelName()).isEqualTo(DEPLOYMENT_NAME);
            assertThat(context.chatRequest().parameters().temperature()).isEqualTo(0.3);
        });
        assertThat(listener.responses).singleElement().satisfies(context -> {
            assertThat(context.invocationId()).isEqualTo(invocationId);
            assertThat(context.attributes()).containsEntry("tenant", "acme");
            assertThat(context.chatResponse().aiMessage().text()).isEqualTo("Hi");
        });
        assertThat(listener.errors).isEmpty();
    }

    @Test
    void listenerSeesErrorWhenRequestCannotBeMapped() {
        UUID invocationId = UUID.randomUUID();

        assertThatThrownBy(() -> chatModel.chat(unmappableRequest(), options(invocationId)))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessageStartingWith(UNSUPPORTED_TOOL_RESULT_CONTENT);
        assertThat(mock.requests()).isEmpty();

        assertThat(listener.requests).singleElement().satisfies(context -> {
            assertThat(context.invocationId()).isEqualTo(invocationId);
        });
        assertThat(listener.responses).isEmpty();
        assertThat(listener.errors).singleElement().satisfies(context -> {
            assertThat(context.invocationId()).isEqualTo(invocationId);
            assertThat(context.attributes()).containsEntry("tenant", "acme");
            assertThat(context.error()).hasMessageStartingWith(UNSUPPORTED_TOOL_RESULT_CONTENT);
        });
    }

    @Test
    void listenerSeesInvocationIdAndAttributesWhenStreaming() throws Exception {
        mock.chat().reply(ChatReply.text("Hi").usage(1, 1));
        UUID invocationId = UUID.randomUUID();

        ChatResponse response = stream(request(), options(invocationId)).get(30, TimeUnit.SECONDS);

        assertThat(response.aiMessage().text()).isEqualTo("Hi");
        assertThat(mock.requests()).hasSize(1);

        assertThat(listener.requests).singleElement().satisfies(context -> {
            assertThat(context.invocationId()).isEqualTo(invocationId);
            assertThat(context.attributes()).containsEntry("tenant", "acme");
            assertThat(context.modelProvider()).isEqualTo(ModelProvider.AZURE_OPEN_AI);
            assertThat(context.chatRequest().parameters().modelName()).isEqualTo(DEPLOYMENT_NAME);
            assertThat(context.chatRequest().parameters().temperature()).isEqualTo(0.3);
        });
        assertThat(listener.responses).singleElement().satisfies(context -> {
            assertThat(context.invocationId()).isEqualTo(invocationId);
            assertThat(context.attributes()).containsEntry("tenant", "acme");
            assertThat(context.chatResponse().aiMessage().text()).isEqualTo("Hi");
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

        CompletableFuture<ChatResponse> response = stream(unmappableRequest(), options(invocationId));

        assertThatThrownBy(() -> response.get(30, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .rootCause()
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessageStartingWith(UNSUPPORTED_TOOL_RESULT_CONTENT);
        assertThat(mock.requests()).isEmpty();

        assertThat(listener.requests).singleElement().satisfies(context -> {
            assertThat(context.invocationId()).isEqualTo(invocationId);
        });
        assertThat(listener.responses).isEmpty();
        assertThat(listener.errors).singleElement().satisfies(context -> {
            assertThat(context.invocationId()).isEqualTo(invocationId);
            assertThat(context.attributes()).containsEntry("tenant", "acme");
            assertThat(context.error()).hasMessageStartingWith(UNSUPPORTED_TOOL_RESULT_CONTENT);
        });
    }

    private static ChatRequest request() {
        return ChatRequest.builder().messages(UserMessage.from("hello")).build();
    }

    /**
     * The OpenAI message mapper rejects a tool result that is not plain text, before anything is sent.
     */
    private static ChatRequest unmappableRequest() {
        return ChatRequest.builder()
                .messages(UserMessage.from("hello"),
                        ToolExecutionResultMessage.builder()
                                .id("call-1")
                                .toolName("getWeather")
                                .contents(ImageContent.from("https://example.com/image.png"))
                                .build())
                .build();
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
