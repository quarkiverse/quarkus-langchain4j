package io.quarkiverse.langchain4j.anthropic.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okForContentType;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.quarkus.test.QuarkusExtensionTest;

class AnthropicAutomaticCachingSmokeTest extends AnthropicSmokeTest {

    private static final String CACHE_CONTROL = """
            { "cache_control" : { "type" : "ephemeral", "ttl" : "1h" } }""";

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.base-url", "http://localhost:%d".formatted(WIREMOCK_PORT))
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.chat-model.cache-automatically", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.chat-model.cache-ttl", "1h");

    @Inject
    ChatModel chatModel;

    @Inject
    StreamingChatModel streamingChatModel;

    @Test
    void blockingRequestEnablesAutomaticCaching() {
        wireMockServer.stubFor(post(urlPathEqualTo("/messages"))
                .willReturn(okJson("""
                        {
                          "id": "msg_01",
                          "type": "message",
                          "role": "assistant",
                          "content": [ { "type": "text", "text": "Hi!" } ],
                          "model": "claude-3-haiku-20240307",
                          "stop_reason": "end_turn",
                          "usage": { "input_tokens": 10, "output_tokens": 2 }
                        }
                        """)));

        assertThat(chatModel.chat("Hello")).isEqualTo("Hi!");

        wireMockServer.verify(1, postRequestedFor(urlPathEqualTo("/messages"))
                .withRequestBody(equalToJson(CACHE_CONTROL, true, true)));
    }

    @Test
    void streamingRequestEnablesAutomaticCaching() {
        wireMockServer.stubFor(post(urlPathEqualTo("/messages"))
                .willReturn(okForContentType(MediaType.SERVER_SENT_EVENTS,
                        """
                                event: message_start
                                data: {"type":"message_start","message":{"id":"msg_01","type":"message","role":"assistant","content":[],"model":"claude-3-haiku-20240307","stop_reason":null,"usage":{"input_tokens":10,"output_tokens":1}}}

                                event: content_block_start
                                data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

                                event: content_block_delta
                                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hi!"}}

                                event: content_block_stop
                                data: {"type":"content_block_stop","index":0}

                                event: message_delta
                                data: {"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"output_tokens":2}}

                                event: message_stop
                                data: {"type":"message_stop"}

                                """)));

        var response = new AtomicReference<ChatResponse>();
        streamingChatModel.chat("Hello", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                response.set(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                response.set(null);
            }
        });

        await().atMost(Duration.ofSeconds(10)).until(() -> response.get() != null);
        assertThat(response.get().aiMessage().text()).isEqualTo("Hi!");

        wireMockServer.verify(1, postRequestedFor(urlPathEqualTo("/messages"))
                .withRequestBody(matchingJsonPath("$.stream", equalToJson("true")))
                .withRequestBody(equalToJson(CACHE_CONTROL, true, true)));
    }
}
