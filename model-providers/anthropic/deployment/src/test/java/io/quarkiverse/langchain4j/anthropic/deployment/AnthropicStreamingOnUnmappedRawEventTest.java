package io.quarkiverse.langchain4j.anthropic.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.okForContentType;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.model.anthropic.AnthropicChatResponseMetadata;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.quarkus.test.QuarkusUnitTest;

class AnthropicStreamingOnUnmappedRawEventTest extends AnthropicSmokeTest {
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.base-url", "http://localhost:%d".formatted(WIREMOCK_PORT));

    @Inject
    StreamingChatModel streamingChatModel;

    @Test
    void onUnmappedRawEventReceivesRawServerSentEvent() {
        // A stream that carries a hosted web search: a server_tool_use block with the query,
        // a web_search_tool_result block with the hits, then the text answer.
        // Only the two text_delta frames and the closing message_stop map to a typed callback;
        // every other frame must be surfaced via onUnmappedRawEvent.
        var eventStream = """
                event: message_start
                data: {"type":"message_start","message":{"id":"msg_01","type":"message","role":"assistant","content":[],"model":"claude-3-haiku-20240307","stop_reason":null,"usage":{"input_tokens":10,"output_tokens":1}}}

                event: content_block_start
                data: {"type":"content_block_start","index":0,"content_block":{"type":"server_tool_use","id":"srvtoolu_01","name":"web_search","input":{}}}

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"{\\"query\\": \\"anthropic\\"}"}}

                event: content_block_stop
                data: {"type":"content_block_stop","index":0}

                event: content_block_start
                data: {"type":"content_block_start","index":1,"content_block":{"type":"web_search_tool_result","tool_use_id":"srvtoolu_01","content":[{"type":"web_search_result","url":"https://a"},{"type":"web_search_result","url":"https://b"},{"type":"web_search_result","url":"https://c"}]}}

                event: content_block_stop
                data: {"type":"content_block_stop","index":1}

                event: content_block_start
                data: {"type":"content_block_start","index":2,"content_block":{"type":"text","text":""}}

                event: ping
                data: {"type":"ping"}

                event: content_block_delta
                data: {"type":"content_block_delta","index":2,"delta":{"type":"text_delta","text":"Three sources "}}

                event: content_block_delta
                data: {"type":"content_block_delta","index":2,"delta":{"type":"text_delta","text":"were consulted."}}

                event: content_block_stop
                data: {"type":"content_block_stop","index":2}

                event: message_delta
                data: {"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"output_tokens":20}}

                event: message_stop
                data: {"type":"message_stop"}

                """;

        wireMockServer.stubFor(
                post(urlPathEqualTo("/messages"))
                        .willReturn(okForContentType(MediaType.SERVER_SENT_EVENTS, eventStream)));

        var streamingResponse = new AtomicReference<ChatResponse>();
        List<Object> rawEvents = new CopyOnWriteArrayList<>();
        streamingChatModel.chat("Search please", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String token) {
            }

            @Override
            public void onUnmappedRawEvent(Object rawEvent) {
                rawEvents.add(rawEvent);
            }

            @Override
            public void onError(Throwable error) {
                fail("Streaming failed: %s".formatted(error.getMessage()), error);
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                streamingResponse.set(response);
            }
        });

        await()
                .atMost(Duration.ofMinutes(1))
                .pollInterval(Duration.ofSeconds(1))
                .until(() -> streamingResponse.get() != null);

        // The two text_delta frames assemble the answer.
        assertThat(streamingResponse.get().aiMessage().text())
                .isEqualTo("Three sources were consulted.");

        // Every frame except the two text deltas and message_stop is unmapped and must be forwarded.
        assertThat(rawEvents)
                .isNotEmpty()
                .doesNotContainNull()
                .allSatisfy(rawEvent -> assertThat(rawEvent).isInstanceOf(ServerSentEvent.class));

        // The complete response must carry Anthropic metadata exposing the raw SSE frames.
        assertThat(streamingResponse.get().metadata())
                .isInstanceOf(AnthropicChatResponseMetadata.class);
        var metadata = (AnthropicChatResponseMetadata) streamingResponse.get().metadata();
        assertThat(metadata.rawServerSentEvents()).isNotEmpty();
    }
}
