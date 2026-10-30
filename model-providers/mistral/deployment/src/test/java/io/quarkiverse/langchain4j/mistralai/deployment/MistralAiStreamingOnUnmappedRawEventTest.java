package io.quarkiverse.langchain4j.mistralai.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okForContentType;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
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

import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.mistralai.MistralAiChatResponseMetadata;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

class MistralAiStreamingOnUnmappedRawEventTest extends WiremockAware {

    private static final String API_KEY = "somekey";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.mistralai.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.mistralai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @Inject
    StreamingChatModel streamingChatModel;

    @BeforeEach
    void setup() {
        resetRequests();
    }

    @Test
    void onUnmappedRawEventReceivesRawServerSentEvent() throws InterruptedException {
        // The first frame carries a text delta (mapped via onPartialResponse). The second frame has an empty
        // delta with only a finish_reason and usage, so it maps to no typed callback and must be forwarded to
        // onUnmappedRawEvent.
        String eventStream = """
                data: {"id":"cmpl-1","object":"chat.completion.chunk","created":1,"model":"mistral-tiny","choices":[{"index":0,"delta":{"role":"assistant","content":"Hello"},"finish_reason":null}]}

                data: {"id":"cmpl-1","object":"chat.completion.chunk","created":1,"model":"mistral-tiny","choices":[{"index":0,"delta":{"content":null},"finish_reason":"stop"}],"usage":{"prompt_tokens":1,"total_tokens":2,"completion_tokens":1}}

                data: [DONE]

                """;

        wiremock().register(
                post(urlEqualTo("/v1/chat/completions"))
                        .withHeader("Authorization", equalTo("Bearer " + API_KEY))
                        .willReturn(okForContentType(MediaType.SERVER_SENT_EVENTS, eventStream)));

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
    }
}
