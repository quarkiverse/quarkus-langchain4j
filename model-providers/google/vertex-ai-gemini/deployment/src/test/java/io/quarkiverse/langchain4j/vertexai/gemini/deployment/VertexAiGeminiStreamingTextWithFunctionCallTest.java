package io.quarkiverse.langchain4j.vertexai.gemini.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Map;
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

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkiverse.langchain4j.mockapi.RawResponse;
import io.quarkus.test.QuarkusExtensionTest;

public class VertexAiGeminiStreamingTextWithFunctionCallTest {

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
    MockModelServer mock;

    @BeforeEach
    void setUp() {
        mock.reset();
    }

    @Test
    void textInChunkWithFunctionCallIsKept() throws Exception {
        // a raw stub: text and a signed function call in one chunk is the Gemini shape under test
        mock.raw().on("POST", STREAM_PATH)
                .replies(RawResponse.of(200, Map.of("Content-Type", "text/event-stream"),
                        """
                                data: {"candidates":[{"content":{"role":"model","parts":[{"text":"Let me check the weather."},{"functionCall":{"name":"getWeather","args":{"city":"Paris"}},"thoughtSignature":"signature-1"}]},"finishReason":"STOP"}],"usageMetadata":{"promptTokenCount":1,"candidatesTokenCount":2,"totalTokenCount":3}}

                                """));

        List<String> partialResponses = new CopyOnWriteArrayList<>();
        CompletableFuture<ChatResponse> response = new CompletableFuture<>();
        streamingChatModel.chat("What is the weather in Paris?", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                partialResponses.add(partialResponse);
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

        AiMessage aiMessage = response.get(30, TimeUnit.SECONDS).aiMessage();
        assertThat(partialResponses).containsExactly("Let me check the weather.");
        assertThat(aiMessage.text()).isEqualTo("Let me check the weather.");
        assertThat(aiMessage.toolExecutionRequests())
                .extracting(ToolExecutionRequest::name, ToolExecutionRequest::id)
                .containsExactly(tuple("getWeather", "signature-1"));
        assertThat(mock.requests()).singleElement()
                .satisfies(request -> assertThat(request.path()).isEqualTo(STREAM_PATH));
    }

    @Singleton
    public static class DummyAuthProvider implements ModelAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            return "Bearer somekey";
        }
    }
}
