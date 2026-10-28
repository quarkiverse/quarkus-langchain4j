package io.quarkiverse.langchain4j.mistralai.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
import dev.langchain4j.model.mistralai.MistralAiChatResponseMetadata;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

class MistralAiStreamingOnUnmappedRawEventTest {

    private static final String API_KEY = "somekey";

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.mistralai.api-key", API_KEY);

    @Inject
    StreamingChatModel streamingChatModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void setup() {
        mock.reset();
    }

    @Test
    void onUnmappedRawEventReceivesRawServerSentEvent() throws InterruptedException {
        // The neutral stream sends the text delta (mapped via onPartialResponse), then a frame with an empty delta
        // carrying only the finish_reason and the usage, which maps to no typed callback and must be forwarded to
        // onUnmappedRawEvent.
        mock.chat().reply(ChatReply.text("Hello").usage(1, 1));

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

        assertThat(rawEvents)
                .isNotEmpty()
                .doesNotContainNull()
                .allSatisfy(rawEvent -> assertThat(rawEvent).isInstanceOf(ServerSentEvent.class));

        assertThat(response.get()).isNotNull();
        assertThat(response.get().metadata()).isInstanceOf(MistralAiChatResponseMetadata.class);
        var metadata = (MistralAiChatResponseMetadata) response.get().metadata();
        assertThat(metadata.rawServerSentEvents()).isNotEmpty();

        CapturedRequest request = mock.chat().singleRequest();
        assertThat(request.path()).isEqualTo("/mistralai/v1/chat/completions");
        assertThat(request.query()).isNull();
        assertThat(request.header("Authorization")).isEqualTo("Bearer " + API_KEY);
        assertThat(request.asChat().stream()).isTrue();
    }
}
