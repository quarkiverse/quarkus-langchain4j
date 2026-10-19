package io.quarkiverse.langchain4j.openai.test;

import static com.github.tomakehurst.wiremock.client.WireMock.okForContentType;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.quarkiverse.langchain4j.openai.common.OpenAiApiException;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

/**
 * OpenAI-compatible servers (vLLM, the LiteLLM proxy, ...) report a failure that happens after the HTTP 200 has been sent
 * as a chunk that only holds an {@code error} object. The provider's error must reach the handler.
 */
public class StreamingChatErrorChunkTest extends OpenAiBaseTest {

    private static final String ERROR = """
            {"error":{"message":"Upstream model overloaded, retry in 30s","type":"server_error","param":null,"code":"overloaded"}}""";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "my-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @Inject
    StreamingChatModel streamingChatModel;

    @BeforeEach
    void reset() {
        resetRequests();
    }

    @Test
    void errorChunkAfterContentCarriesTheProviderError() throws InterruptedException {
        var eventStream = """
                data: {"id":"chatcmpl-1","object":"chat.completion.chunk","created":1,"model":"gpt-4o-mini","choices":[{"index":0,"delta":{"role":"assistant","content":"Half "},"finish_reason":null}]}

                data: {"id":"chatcmpl-1","object":"chat.completion.chunk","created":1,"model":"gpt-4o-mini","choices":[{"index":0,"delta":{"content":"an ans"},"finish_reason":null}]}

                data: %s

                """
                .formatted(ERROR);
        wiremock().register(post(urlEqualTo("/v1/chat/completions"))
                .willReturn(okForContentType(MediaType.SERVER_SENT_EVENTS, eventStream)));

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
    }
}
