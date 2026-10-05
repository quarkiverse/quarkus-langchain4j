package io.quarkiverse.langchain4j.anthropic.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okForContentType;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.ws.rs.core.MediaType;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.quarkiverse.langchain4j.anthropic.QuarkusAnthropicClient;
import io.quarkus.test.QuarkusUnitTest;

/**
 * A gateway in front of Anthropic authenticates on its own header, so {@code customHeaders} passed
 * to the model builder has to reach the request. The client built by the extension is the one the
 * models actually use, so this covers the Quarkus substitution rather than langchain4j's own
 * client.
 */
class AnthropicCustomHeadersSmokeTest extends AnthropicSmokeTest {

    private static final String GATEWAY_HEADER = "X-API-Gateway-Key";
    private static final String GATEWAY_VALUE = "gateway-secret";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.base-url",
                    "http://localhost:%d".formatted(WIREMOCK_PORT));

    @Test
    void customHeadersReachBlockingRequests() {
        wireMockServer.stubFor(
                post(urlPathEqualTo("/messages"))
                        .withHeader(GATEWAY_HEADER, equalTo(GATEWAY_VALUE))
                        .willReturn(okJson("""
                                {
                                  "id": "msg_01EwYzt25EaVu4rNUewCkdP3",
                                  "type": "message",
                                  "role": "assistant",
                                  "content": [
                                    {
                                      "type": "text",
                                      "text": "Hello!"
                                    }
                                  ],
                                  "model": "claude-3-haiku-20240307",
                                  "stop_reason": "end_turn",
                                  "stop_sequence": null,
                                  "usage": {
                                    "input_tokens": 14,
                                    "output_tokens": 5
                                  }
                                }
                                """)));

        AnthropicChatModel model = AnthropicChatModel.builder()
                .apiKey(API_KEY)
                .baseUrl("http://localhost:%d".formatted(WIREMOCK_PORT))
                .customHeaders(Map.of(GATEWAY_HEADER, GATEWAY_VALUE))
                .build();

        // The assertions below only mean something if the extension's client is the one sending
        // the request, so pin that rather than assuming the service loader picked it.
        assertThat(clientOf(model)).isInstanceOf(QuarkusAnthropicClient.class);

        assertThat(model.chat("hello")).isEqualTo("Hello!");

        var request = wireMockServer.getAllServeEvents().get(0).getRequest();
        assertThat(request.getHeader(GATEWAY_HEADER)).isEqualTo(GATEWAY_VALUE);
        // The provider credential still travels on its own header.
        assertThat(request.getHeader("x-api-key")).isEqualTo(API_KEY);
    }

    private static Object clientOf(Object model) {
        try {
            var field = model.getClass().getDeclaredField("client");
            field.setAccessible(true);
            return field.get(model);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not read the client from " + model.getClass(), e);
        }
    }

    @Test
    void customHeadersReachStreamingRequests() {
        var eventStream = """
                event: message_start
                data: {"type":"message_start","message":{"id":"msg_01","type":"message","role":"assistant","content":[],"model":"claude-3-haiku-20240307","stop_reason":null,"usage":{"input_tokens":10,"output_tokens":1}}}

                event: content_block_start
                data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello!"}}

                event: content_block_stop
                data: {"type":"content_block_stop","index":0}

                event: message_delta
                data: {"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"output_tokens":20}}

                event: message_stop
                data: {"type":"message_stop"}

                """;

        wireMockServer.stubFor(
                post(urlPathEqualTo("/messages"))
                        .withHeader(GATEWAY_HEADER, equalTo(GATEWAY_VALUE))
                        .willReturn(okForContentType(MediaType.SERVER_SENT_EVENTS, eventStream)));

        AnthropicStreamingChatModel model = AnthropicStreamingChatModel.builder()
                .apiKey(API_KEY)
                .baseUrl("http://localhost:%d".formatted(WIREMOCK_PORT))
                .customHeaders(Map.of(GATEWAY_HEADER, GATEWAY_VALUE))
                .build();

        var response = new AtomicReference<ChatResponse>();
        model.chat("hello", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String token) {
            }

            @Override
            public void onError(Throwable error) {
                fail("Streaming failed: %s".formatted(error.getMessage()), error);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                response.set(completeResponse);
            }
        });

        await().atMost(Duration.ofSeconds(10)).until(() -> response.get() != null);
        assertThat(response.get().aiMessage().text()).isEqualTo("Hello!");

        var request = wireMockServer.getAllServeEvents().get(0).getRequest();
        assertThat(request.getHeader(GATEWAY_HEADER)).isEqualTo(GATEWAY_VALUE);
        assertThat(request.getHeader("x-api-key")).isEqualTo(API_KEY);
    }
}
