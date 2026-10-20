package io.quarkiverse.langchain4j.openai.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class ResponsesStreamingChatModelTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.openai.chat-model.mode", "responses")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "my-key")
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true");

    @Inject
    StreamingChatModel streamingChatModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void streamingChat() {
        mock.chat().reply(ChatReply.text("Hallo Welt").chunks(List.of("Hallo", " Welt")).usage(1, 2));

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

        CapturedRequest request = mock.chat().singleRequest();
        assertThat(request.path()).isEqualTo("/v1/responses");
        assertThat(request.query()).isNull();
        assertThat(request.body())
                .doesNotContain("\"temperature\"")
                .doesNotContain("\"top_p\"");
    }
}
