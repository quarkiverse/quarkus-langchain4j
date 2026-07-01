package io.quarkiverse.langchain4j.openai.test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okForContentType;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiResponsesChatModel;
import dev.langchain4j.model.openai.OpenAiResponsesStreamingChatModel;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusUnitTest;

public class OpenAiResponsesApiTest extends OpenAiBaseTest {

    private static final String RESPONSES_PATH = "/v1/responses";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.api", "responses");

    @Inject
    ChatModel chatModel;

    @Inject
    StreamingChatModel streamingChatModel;

    @BeforeEach
    void reset() {
        resetRequests();
        wiremock().resetMappings();
    }

    @Test
    void blockingChatUsesResponsesEndpoint() throws IOException {
        assertThat(ClientProxy.unwrap(chatModel)).isInstanceOf(OpenAiResponsesChatModel.class);

        stubBlockingResponse("Hello from Responses API");

        String response = chatModel.chat("hello");
        assertThat(response).isEqualTo("Hello from Responses API");

        wiremock().verify(postRequestedFor(urlEqualTo(RESPONSES_PATH)));

        LoggedRequest loggedRequest = singleLoggedRequest();
        assertThat(loggedRequest.getUrl()).isEqualTo(RESPONSES_PATH);
        assertThat(loggedRequest.getHeader("Authorization")).isEqualTo("Bearer whatever");

        var requestAsMap = getRequestAsMap();
        assertThat(requestAsMap)
                .containsEntry("model", "gpt-4o-mini")
                .containsKey("input");
    }

    @Test
    void streamingChatUsesResponsesEndpoint() {
        assertThat(ClientProxy.unwrap(streamingChatModel)).isInstanceOf(OpenAiResponsesStreamingChatModel.class);

        var eventStream = """
                data: {"type":"response.output_text.delta","delta":"Hello"}

                data: {"type":"response.output_text.delta","delta":" world"}

                data: {"type":"response.completed","response":{"id":"resp_stream","object":"response","model":"gpt-4o-mini","status":"completed","output":[{"type":"message","content":[{"type":"output_text","text":"Hello world"}]}]}}

                data: [DONE]
                """;

        wiremock().register(
                post(urlEqualTo(RESPONSES_PATH))
                        .willReturn(okForContentType(MediaType.SERVER_SENT_EVENTS, eventStream)));

        var streamingResponse = new AtomicReference<AiMessage>();
        streamingChatModel.chat("hello", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String token) {
            }

            @Override
            public void onError(Throwable error) {
                fail("Streaming failed: %s".formatted(error.getMessage()), error);
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                streamingResponse.set(response.aiMessage());
            }
        });

        await()
                .atMost(Duration.ofMinutes(1))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> streamingResponse.get() != null);

        assertThat(streamingResponse.get().text()).isEqualTo("Hello world");
        wiremock().verify(postRequestedFor(urlEqualTo(RESPONSES_PATH)));
    }

    private void stubBlockingResponse(String text) {
        wiremock().register(
                post(urlEqualTo(RESPONSES_PATH))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                        {
                                          "id": "resp_test",
                                          "object": "response",
                                          "created_at": 1733923283,
                                          "model": "gpt-4o-mini",
                                          "status": "completed",
                                          "output": [
                                            {
                                              "type": "message",
                                              "content": [
                                                {
                                                  "type": "output_text",
                                                  "text": "%s"
                                                }
                                              ]
                                            }
                                          ],
                                          "usage": {
                                            "input_tokens": 10,
                                            "output_tokens": 5,
                                            "total_tokens": 15
                                          }
                                        }
                                        """.formatted(text))));
    }
}
