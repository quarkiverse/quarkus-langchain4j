package io.quarkiverse.langchain4j.anthropic.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.not;
import static com.github.tomakehurst.wiremock.client.WireMock.okForContentType;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusUnitTest;

class AnthropicSamplingParametersTest extends AnthropicSmokeTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.chat-model.top-k", "20")
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.chat-model.top-p", "0.8")
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.chat-model.temperature", "0.2")
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.base-url", "http://localhost:%d".formatted(WIREMOCK_PORT));

    @Inject
    ChatModel chatModel;

    @Inject
    StreamingChatModel streamingChatModel;

    @Test
    void shouldSendConfiguredSamplingParameters() throws Exception {
        wireMockServer.stubFor(
                post(urlPathEqualTo("/messages"))
                        .withHeader("x-api-key", equalTo(API_KEY))
                        .withHeader("anthropic-version", not(absent()))
                        .willReturn(okJson("""
                                {
                                  "type": "message",
                                  "role": "assistant",
                                  "content": [{"type": "text", "text": "ok"}],
                                  "stop_reason": "end_turn",
                                  "usage": {"input_tokens": 1, "output_tokens": 1}
                                }
                                """)));

        chatModel.chat("Hello");

        assertThat(wireMockServer.getAllServeEvents()).hasSize(1);
        JsonNode request = MAPPER.readTree(wireMockServer.getAllServeEvents().get(0).getRequest().getBodyAsString());
        assertThat(request.path("top_k").asInt()).isEqualTo(20);
        assertThat(request.path("top_p").asDouble()).isEqualTo(0.8);
        assertThat(request.path("temperature").asDouble()).isEqualTo(0.2);
    }

    @Test
    void shouldSendConfiguredSamplingParametersForStreaming() throws Exception {
        assertThat(ClientProxy.unwrap(streamingChatModel))
                .isInstanceOf(AnthropicStreamingChatModel.class);

        wireMockServer.stubFor(
                post(urlPathEqualTo("/messages"))
                        .withHeader("x-api-key", equalTo(API_KEY))
                        .withHeader("anthropic-version", not(absent()))
                        .willReturn(okForContentType(MediaType.SERVER_SENT_EVENTS,
                                """
                                        event: message_start
                                        data: {"type":"message_start","message":{"id":"msg_1","type":"message","role":"assistant","content":[],"model":"claude-3-haiku-20240307","stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":1,"output_tokens":0}}}

                                        event: content_block_start
                                        data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

                                        event: content_block_delta
                                        data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"ok"}}

                                        event: content_block_stop
                                        data: {"type":"content_block_stop","index":0}

                                        event: message_delta
                                        data: {"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"output_tokens":1}}

                                        event: message_stop
                                        data: {"type":"message_stop"}

                                        """)));

        AtomicReference<ChatResponse> response = new AtomicReference<>();
        streamingChatModel.chat("Hello", new StreamingChatResponseHandler() {
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

        await()
                .atMost(Duration.ofMinutes(1))
                .until(() -> response.get() != null);

        assertThat(wireMockServer.getAllServeEvents()).hasSize(1);
        JsonNode request = MAPPER.readTree(wireMockServer.getAllServeEvents().get(0).getRequest().getBodyAsString());
        assertThat(request.path("top_k").asInt()).isEqualTo(20);
        assertThat(request.path("top_p").asDouble()).isEqualTo(0.8);
        assertThat(request.path("temperature").asDouble()).isEqualTo(0.2);
    }
}
