package io.quarkiverse.langchain4j.openai.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkiverse.langchain4j.mockapi.RawResponse;
import io.quarkiverse.langchain4j.mockapi.SseEvent;
import io.quarkus.test.QuarkusExtensionTest;

public class StreamingChatOnUnmappedRawEventTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "my-key")
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            // about the Chat Completions API: without a base URL of its own the model would target the Responses API
            .overrideConfigKey("quarkus.langchain4j.openai.chat-model.mode", "chat-completion");
    @Inject
    StreamingChatModel streamingChatModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void onUnmappedRawEventReceivesRawServerSentEvent() throws InterruptedException {
        // The 2nd and 3rd frames carry nothing that langchain4j maps to a typed callback
        // (empty delta with finish_reason, and a usage-only chunk with no choices), so they
        // are delivered to onUnmappedRawEvent as raw events. Served raw so the test does not depend on how the
        // mock frames a neutral reply.
        mock.raw().on("POST", "/v1/chat/completions").replies(RawResponse.sse(200,
                Map.of("Content-Type", "text/event-stream"), List.of(
                        SseEvent.of(null,
                                "{\"id\":\"chatcmpl-1\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"gpt-4o-mini\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"Hallo\"},\"finish_reason\":null}]}"),
                        SseEvent.of(null,
                                "{\"id\":\"chatcmpl-1\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"gpt-4o-mini\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}"),
                        SseEvent.of(null,
                                "{\"id\":\"chatcmpl-1\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"gpt-4o-mini\",\"choices\":[],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}"),
                        SseEvent.of(null, "[DONE]"))));

        List<Object> rawEvents = new CopyOnWriteArrayList<>();
        var latch = new CountDownLatch(1);
        streamingChatModel.chat("hello", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String token) {
            }

            @Override
            public void onUnmappedRawEvent(Object rawEvent) {
                rawEvents.add(rawEvent);
            }

            @Override
            public void onError(Throwable error) {
                latch.countDown();
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                latch.countDown();
            }
        });

        if (!latch.await(1, TimeUnit.MINUTES)) {
            fail("Streaming did not complete in time");
        }

        assertThat(rawEvents).isNotEmpty();
        assertThat(rawEvents).doesNotContainNull();
        assertThat(rawEvents).allSatisfy(rawEvent -> {
            assertThat(rawEvent).isInstanceOf(ServerSentEvent.class);
            assertThat(((ServerSentEvent) rawEvent).data()).contains("chat.completion.chunk");
        });

        assertThat(mock.requests()).hasSize(1);
        CapturedRequest request = mock.requests().get(0);
        assertThat(request.path()).isEqualTo("/v1/chat/completions");
        assertThat(request.query()).isNull();
    }
}
