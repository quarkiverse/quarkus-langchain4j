package io.quarkiverse.langchain4j.vertexai.gemini.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class VertexAiGeminiStreamingOnUnmappedRawEventTest extends WiremockAware {

    private static final String API_KEY = "somekey";
    private static final String CHAT_MODEL_ID = "gemini-2.5-flash";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.gemini.base-url", WiremockAware.wiremockUrlForConfig());

    @Inject
    StreamingChatModel streamingChatModel;

    @Test
    void onUnmappedRawEventReceivesRawServerSentEvent() throws InterruptedException {
        // The first two frames carry text (mapped via onPartialResponse). The last frame carries only a
        // finishReason and usage with no content parts, so it maps to no typed callback and must be forwarded
        // to onUnmappedRawEvent.
        String eventStream = """
                data: {"candidates":[{"content":{"role":"model","parts":[{"text":"Hello"}]}}]}

                data: {"candidates":[{"content":{"role":"model","parts":[{"text":" world"}]}}]}

                data: {"candidates":[{"finishReason":"STOP"}],"usageMetadata":{"promptTokenCount":1,"candidatesTokenCount":2,"totalTokenCount":3},"modelVersion":"gemini-2.5-flash","responseId":"resp-1"}

                """;

        wiremock().register(
                post(urlPathEqualTo(
                        String.format("/v1/projects/dummy/locations/dummy/publishers/google/models/%s:streamGenerateContent",
                                CHAT_MODEL_ID)))
                        .withHeader("Authorization", equalTo("Bearer " + API_KEY))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "text/event-stream")
                                .withBody(eventStream)));

        AtomicReference<ChatResponse> response = new AtomicReference<>();
        List<Object> rawEvents = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        streamingChatModel.chat("hello", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String token) {
            }

            @Override
            public void onUnmappedRawEvent(Object rawEvent) {
                rawEvents.add(rawEvent);
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
        assertThat(response.get().aiMessage().text()).isEqualTo("Hello world");

        assertThat(rawEvents)
                .isNotEmpty()
                .doesNotContainNull()
                .allSatisfy(rawEvent -> assertThat(rawEvent).isInstanceOf(ServerSentEvent.class));
    }

    @Singleton
    public static class DummyAuthProvider implements ModelAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            return "Bearer " + API_KEY;
        }
    }
}
