package io.quarkiverse.langchain4j.vertexai.gemini.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkiverse.langchain4j.mockapi.RawResponse;
import io.quarkus.test.QuarkusExtensionTest;

public class VertexAiGeminiStreamingThoughtSignatureTest {

    private static final String API_KEY = "somekey";
    private static final String CHAT_MODEL_ID = "gemini-2.5-flash";

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true");

    @Inject
    StreamingChatModel streamingChatModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    private static final String STREAM_PATH = String.format(
            "/vertexai-gemini/v1/projects/mock-project/locations/us-central1/publishers/google/models/%s:streamGenerateContent",
            CHAT_MODEL_ID);

    @Test
    void streamedFunctionCallThoughtSignatureIsSentBackOnNextTurn() throws InterruptedException {
        String eventStream = """
                data: {"candidates":[{"content":{"role":"model","parts":[{"functionCall":{"name":"getWeather","args":{"city":"Paris"}},"thoughtSignature":"signature-1"},{"functionCall":{"name":"getTime","args":{"city":"Paris"}}}]}}]}

                data: {"candidates":[{"finishReason":"STOP"}],"usageMetadata":{"promptTokenCount":1,"candidatesTokenCount":2,"totalTokenCount":3},"modelVersion":"gemini-2.5-flash","responseId":"resp-1"}

                """;

        mock.raw().on("POST", STREAM_PATH)
                .replies(RawResponse.of(200, Map.of("Content-Type", "text/event-stream"), eventStream));

        UserMessage userMessage = UserMessage.from("What is the weather and time in Paris?");
        ChatResponse response = chat(List.of(userMessage));

        assertThat(response.finishReason()).isEqualTo(FinishReason.TOOL_EXECUTION);
        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.toolExecutionRequests())
                .extracting(ToolExecutionRequest::name, ToolExecutionRequest::id)
                .containsExactly(
                        tuple("getWeather", "signature-1"),
                        tuple("getTime", null));

        chat(List.of(userMessage, aiMessage,
                ToolExecutionResultMessage.from(aiMessage.toolExecutionRequests().get(0), "sunny"),
                ToolExecutionResultMessage.from(aiMessage.toolExecutionRequests().get(1), "12:00")));

        List<CapturedRequest> requests = mock.requests();
        assertThat(requests).hasSize(2).allSatisfy(request -> {
            assertThat(request.path()).isEqualTo(STREAM_PATH);
            assertThat(request.header("Authorization")).isEqualTo("Bearer " + API_KEY);
        });
        assertThat(requests).filteredOn(request -> request.body().contains("\"thoughtSignature\":\"signature-1\""))
                .hasSize(1);
    }

    private ChatResponse chat(List<ChatMessage> messages) throws InterruptedException {
        AtomicReference<ChatResponse> response = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        streamingChatModel.chat(messages, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String token) {
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                response.set(completeResponse);
                latch.countDown();
            }

            @Override
            public void onError(Throwable throwable) {
                fail("streaming failed: %s".formatted(throwable.getMessage()), throwable);
                latch.countDown();
            }
        });

        assertThat(latch.await(30, TimeUnit.SECONDS))
                .as("streaming did not complete within the timeout")
                .isTrue();
        assertThat(response.get()).isNotNull();
        return response.get();
    }

    @Singleton
    public static class DummyAuthProvider implements ModelAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            return "Bearer " + API_KEY;
        }
    }
}
