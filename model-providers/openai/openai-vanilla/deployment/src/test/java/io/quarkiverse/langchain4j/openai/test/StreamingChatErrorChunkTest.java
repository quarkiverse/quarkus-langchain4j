package io.quarkiverse.langchain4j.openai.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkiverse.langchain4j.mockapi.RawResponse;
import io.quarkiverse.langchain4j.mockapi.SseEvent;
import io.quarkiverse.langchain4j.openai.common.OpenAiApiException;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * OpenAI-compatible servers (vLLM, the LiteLLM proxy, ...) report a failure that happens after the HTTP 200 has been sent
 * as a chunk that only holds an {@code error} object. The provider's error must reach the handler.
 */
public class StreamingChatErrorChunkTest {

    private static final String ERROR = """
            {"error":{"message":"Upstream model overloaded, retry in 30s","type":"server_error","param":null,"code":"overloaded"}}""";

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
    void errorChunkAfterContentCarriesTheProviderError() throws InterruptedException {
        // a chunk that only holds an error object is not something the neutral reply model can express
        mock.raw().on("POST", "/v1/chat/completions").replies(RawResponse.sse(200,
                Map.of("Content-Type", "text/event-stream"), List.of(
                        SseEvent.of(null,
                                "{\"id\":\"chatcmpl-1\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"gpt-4o-mini\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"Half \"},\"finish_reason\":null}]}"),
                        SseEvent.of(null,
                                "{\"id\":\"chatcmpl-1\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"gpt-4o-mini\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"an ans\"},\"finish_reason\":null}]}"),
                        SseEvent.of(null, ERROR))));

        StringBuilder partialText = new StringBuilder();
        AtomicReference<Throwable> error = new AtomicReference<>();
        var latch = new CountDownLatch(1);
        streamingChatModel.chat("hello", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String token) {
                partialText.append(token);
            }

            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
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

        assertThat(partialText).hasToString("Half an ans");
        assertThat(error.get())
                .isInstanceOf(OpenAiApiException.class)
                .hasMessageContaining("Upstream model overloaded, retry in 30s");
        assertThat(((OpenAiApiException) error.get()).errorBody()).isEqualTo(ERROR);

        assertThat(mock.requests()).hasSize(1);
        CapturedRequest request = mock.requests().get(0);
        assertThat(request.path()).isEqualTo("/v1/chat/completions");
        assertThat(request.query()).isNull();
    }
}
