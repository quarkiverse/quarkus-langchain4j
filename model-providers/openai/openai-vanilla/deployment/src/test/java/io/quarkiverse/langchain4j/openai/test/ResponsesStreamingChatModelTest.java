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

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class ResponsesStreamingChatModelTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.openai.chat-model.mode", "responses")
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
    void streamingChat() {
        var eventStream = """
                data: {"type":"response.output_text.delta","delta":"Hallo"}

                data: {"type":"response.output_text.delta","delta":" Welt"}

                data: {"type":"response.completed","response":{"id":"resp_1","model":"gpt-4o-mini","status":"completed","output":[{"type":"message","role":"assistant","content":[{"type":"output_text","text":"Hallo Welt"}]}],"usage":{"input_tokens":1,"output_tokens":2,"total_tokens":3}}}

                data: [DONE]
                """;

        wiremock().register(post(urlEqualTo("/v1/responses"))
                .willReturn(okForContentType(MediaType.SERVER_SENT_EVENTS, eventStream)));

        var completeResponse = new AtomicReference<AiMessage>();
        var error = new AtomicReference<Throwable>();
        var partialTokens = new StringBuilder();
        var latch = new CountDownLatch(1);
        streamingChatModel.chat("Translate to German: Hello world!", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String token) {
                partialTokens.append(token);
            }

            @Override
            public void onError(Throwable t) {
                error.set(t);
                latch.countDown();
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                completeResponse.set(response.aiMessage());
                latch.countDown();
            }
        });

        try {
            if (!latch.await(1, TimeUnit.MINUTES)) {
                fail("Streaming did not complete in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Interrupted while waiting for streaming response", e);
        }

        if (error.get() != null) {
            fail("Streaming failed: %s".formatted(error.get().getMessage()), error.get());
        }

        assertThat(partialTokens.toString()).isEqualTo("Hallo Welt");
        assertThat(completeResponse.get().text()).isEqualTo("Hallo Welt");
    }
}
