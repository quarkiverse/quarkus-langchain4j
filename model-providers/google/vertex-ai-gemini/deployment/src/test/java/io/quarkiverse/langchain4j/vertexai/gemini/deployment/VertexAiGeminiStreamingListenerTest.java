package io.quarkiverse.langchain4j.vertexai.gemini.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusExtensionTest;

public class VertexAiGeminiStreamingListenerTest extends WiremockAware {

    private static final String CHAT_MODEL_ID = "gemini-3-flash-preview";
    private static final String STREAM_URL = String.format(
            "/v1/projects/dummy/locations/dummy/publishers/google/models/%s:streamGenerateContent", CHAT_MODEL_ID);

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.gemini.base-url", WiremockAware.wiremockUrlForConfig())
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.gemini.chat-model.model-id", CHAT_MODEL_ID);

    @Inject
    StreamingChatModel streamingChatModel;

    @Inject
    RecordingListener listener;

    @BeforeEach
    void setUp() {
        listener.responses.clear();
        listener.errors.clear();
    }

    @Test
    void listenerIsNotifiedOfStreamedResponse() throws Exception {
        wiremock().register(post(urlPathEqualTo(STREAM_URL))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(
                                """
                                        data: {"candidates":[{"content":{"role":"model","parts":[{"text":"Hi"}]},"finishReason":"STOP"}],"usageMetadata":{"promptTokenCount":1,"candidatesTokenCount":1,"totalTokenCount":2}}

                                        """)));

        stream().get(30, TimeUnit.SECONDS);

        assertThat(listener.responses).singleElement()
                .satisfies(context -> assertThat(context.chatResponse().aiMessage().text()).isEqualTo("Hi"));
        assertThat(listener.errors).isEmpty();
    }

    @Test
    void listenerIsNotifiedOfStreamingError() {
        wiremock().register(post(urlPathEqualTo(STREAM_URL)).willReturn(aResponse().withStatus(500)));

        assertThat(stream()).failsWithin(30, TimeUnit.SECONDS);

        assertThat(listener.errors).hasSize(1);
        assertThat(listener.responses).isEmpty();
    }

    private CompletableFuture<ChatResponse> stream() {
        CompletableFuture<ChatResponse> response = new CompletableFuture<>();
        streamingChatModel.chat("hello", new StreamingChatResponseHandler() {
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

        final List<ChatModelResponseContext> responses = new CopyOnWriteArrayList<>();
        final List<ChatModelErrorContext> errors = new CopyOnWriteArrayList<>();

        @Override
        public void onResponse(ChatModelResponseContext responseContext) {
            responses.add(responseContext);
        }

        @Override
        public void onError(ChatModelErrorContext errorContext) {
            errors.add(errorContext);
        }
    }

    @Singleton
    public static class DummyAuthProvider implements ModelAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            return "Bearer somekey";
        }
    }
}
