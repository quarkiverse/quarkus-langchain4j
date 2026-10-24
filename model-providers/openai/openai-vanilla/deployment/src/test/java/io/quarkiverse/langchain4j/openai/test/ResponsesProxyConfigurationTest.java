package io.quarkiverse.langchain4j.openai.test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
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

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * The Responses path honors the proxy registry like the chat-completions path does. The base URL points at a host that
 * does not exist, so the request can only succeed if it is sent through the proxy (WireMock).
 */
public class ResponsesProxyConfigurationTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.mode", "responses")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "my-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url", "http://openai.invalid/v1")
            .overrideRuntimeConfigKey("quarkus.proxy.host", "localhost")
            .overrideRuntimeConfigKey("quarkus.proxy.port", "${quarkus.wiremock.devservices.port}");

    @Inject
    ChatModel chatModel;

    @Inject
    StreamingChatModel streamingChatModel;

    @BeforeEach
    void setUp() {
        resetMappings();
        resetRequests();
    }

    @Test
    void blockingRequestGoesThroughTheProxy() {
        wiremock().register(post(urlEqualTo("/v1/responses"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "resp_1",
                                  "model": "gpt-6-luna",
                                  "status": "completed",
                                  "output": [
                                    {
                                      "type": "message",
                                      "role": "assistant",
                                      "content": [ { "type": "output_text", "text": "via proxy" } ]
                                    }
                                  ],
                                  "usage": { "input_tokens": 1, "output_tokens": 2, "total_tokens": 3 }
                                }
                                """)));

        assertThat(chatModel.chat("hello")).isEqualTo("via proxy");
        assertThat(singleLoggedRequest().getHeader("Host")).isEqualTo("openai.invalid");
    }

    @Test
    void streamingRequestGoesThroughTheProxy() throws InterruptedException {
        var eventStream = """
                data: {"type":"response.output_text.delta","delta":"via proxy"}

                data: {"type":"response.completed","response":{"id":"resp_1","model":"gpt-6-luna","status":"completed","output":[{"type":"message","role":"assistant","content":[{"type":"output_text","text":"via proxy"}]}],"usage":{"input_tokens":1,"output_tokens":2,"total_tokens":3}}}

                data: [DONE]
                """;
        wiremock().register(post(urlEqualTo("/v1/responses"))
                .willReturn(okForContentType(MediaType.SERVER_SENT_EVENTS, eventStream)));

        var text = new AtomicReference<String>();
        var error = new AtomicReference<Throwable>();
        var latch = new CountDownLatch(1);
        streamingChatModel.chat("hello", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String token) {
            }

            @Override
            public void onError(Throwable t) {
                error.set(t);
                latch.countDown();
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                text.set(response.aiMessage().text());
                latch.countDown();
            }
        });
        if (!latch.await(1, TimeUnit.MINUTES)) {
            fail("Streaming did not complete in time");
        }

        assertThat(error.get()).isNull();
        assertThat(text.get()).isEqualTo("via proxy");
        assertThat(singleLoggedRequest().getHeader("Host")).isEqualTo("openai.invalid");
    }
}
