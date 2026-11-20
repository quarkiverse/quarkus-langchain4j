package io.quarkiverse.langchain4j.vertexai.gemini.deployment;

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
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class VertexAiGeminiStreamingListenerTest {

    private static final String CHAT_MODEL_ID = "gemini-3-flash-preview";
    private static final String STREAM_PATH = String.format(
            "/vertexai-gemini/v1/projects/mock-project/locations/us-central1/publishers/google/models/%s:streamGenerateContent",
            CHAT_MODEL_ID);

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.gemini.chat-model.model-id", CHAT_MODEL_ID);

    @Inject
    StreamingChatModel streamingChatModel;

    @Inject
    RecordingListener listener;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void setUp() {
        mock.reset();
        listener.responses.clear();
        listener.errors.clear();
    }

    @Test
    void listenerIsNotifiedOfStreamedResponse() throws Exception {
        mock.chat().reply(ChatReply.text("Hi").usage(1, 1));

        stream().get(30, TimeUnit.SECONDS);

        assertThat(listener.responses).singleElement()
                .satisfies(context -> assertThat(context.chatResponse().aiMessage().text()).isEqualTo("Hi"));
        assertThat(listener.errors).isEmpty();
        assertThat(mock.requests()).singleElement()
                .satisfies(request -> assertThat(request.path()).isEqualTo(STREAM_PATH));
    }

    @Test
    void listenerIsNotifiedOfStreamingError() {
        mock.chat().fail(500, "the mock is unavailable");

        assertThat(stream()).failsWithin(30, TimeUnit.SECONDS);

        assertThat(listener.errors).hasSize(1);
        assertThat(listener.responses).isEmpty();
        assertThat(mock.requests()).isNotEmpty()
                .allSatisfy(request -> assertThat(request.path()).isEqualTo(STREAM_PATH));
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
