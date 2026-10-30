package io.quarkiverse.langchain4j.vertexai.gemini.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
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
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusExtensionTest;

public class VertexAiGeminiStreamingThoughtSignatureTest extends WiremockAware {

    private static final String API_KEY = "somekey";
    private static final String CHAT_MODEL_ID = "gemini-2.5-flash";

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.gemini.base-url", WiremockAware.wiremockUrlForConfig());

    @Inject
    StreamingChatModel streamingChatModel;

    private static final String STREAM_URL = String.format(
            "/v1/projects/dummy/locations/dummy/publishers/google/models/%s:streamGenerateContent", CHAT_MODEL_ID);

    @Test
    void streamedFunctionCallThoughtSignatureIsSentBackOnNextTurn() throws InterruptedException {
        String eventStream = """
                data: {"candidates":[{"content":{"role":"model","parts":[{"functionCall":{"name":"getWeather","args":{"city":"Paris"}},"thoughtSignature":"signature-1"},{"functionCall":{"name":"getTime","args":{"city":"Paris"}}}]}}]}

                data: {"candidates":[{"finishReason":"STOP"}],"usageMetadata":{"promptTokenCount":1,"candidatesTokenCount":2,"totalTokenCount":3},"modelVersion":"gemini-2.5-flash","responseId":"resp-1"}

                """;

        wiremock().register(
                post(urlPathEqualTo(STREAM_URL))
                        .withHeader("Authorization", equalTo("Bearer " + API_KEY))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "text/event-stream")
                                .withBody(eventStream)));

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

        wiremock().verifyThat(exactly(1), postRequestedFor(urlPathEqualTo(STREAM_URL))
                .withRequestBody(containing("\"thoughtSignature\":\"signature-1\"")));
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
