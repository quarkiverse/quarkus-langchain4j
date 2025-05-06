package io.quarkiverse.langchain4j.anthropic.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.not;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusUnitTest;

class AnthropicChatLanguageModelSmokeTest extends AnthropicSmokeTest {
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.log-requests", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.log-responses", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.base-url", "http://localhost:%d".formatted(WIREMOCK_PORT));

    @Inject
    ChatModel chatModel;

    @Test
    void blocking() {
        assertThat(ClientProxy.unwrap(chatModel))
                .isInstanceOf(AnthropicChatModel.class);

        // NOTE: I got this response JSON directly from Claude
        // See https://docs.anthropic.com/claude/reference/messages_post
        wireMockServer.stubFor(
                post(urlPathEqualTo("/messages"))
                        .withHeader("x-api-key", equalTo(API_KEY))
                        .withHeader("anthropic-version", not(absent()))
                        .willReturn(
                                okJson("""
                                          {
                                            "id": "msg_01EwYzt25EaVu4rNUewCkdP3",
                                            "type": "message",
                                            "role": "assistant",
                                            "content": [
                                                {
                                                    "type": "text",
                                                    "text": "Hello! As an AI language model, I don't have feelings or a physical state, but I'm functioning properly and ready to assist you with any questions or tasks you may have. How can I help you today?"
                                                }
                                            ],
                                            "model": "claude-3-haiku-20240307",
                                            "stop_reason": "end_turn",
                                            "stop_sequence": null,
                                            "usage": {
                                                "input_tokens": 14,
                                                "output_tokens": 47
                                            }
                                        }
                                        """)));

        var response = chatModel.chat("Hello, how are you today?");
        assertThat(response)
                .isNotNull()
                .isEqualTo(
                        "Hello! As an AI language model, I don't have feelings or a physical state, but I'm functioning properly and ready to assist you with any questions or tasks you may have. How can I help you today?");

        assertThat(wireMockServer.getAllServeEvents())
                .hasSize(1);

        var serveEvent = wireMockServer.getAllServeEvents().get(0);
        var loggedRequest = serveEvent.getRequest();

        assertThat(loggedRequest.getHeader("User-Agent"))
                .isEqualTo("Quarkus REST Client");

        var requestBody = """
                {
                  "model" : "claude-3-haiku-20240307",
                  "messages" : [ {
                    "role" : "user",
                    "content" : [ {
                      "type" : "text",
                      "text" : "Hello, how are you today?"
                    } ]
                  } ],
                  "system" : [ ],
                  "max_tokens" : 1024,
                  "stream" : false,
                  "top_k" : 40,
                  "tools" : [ ]
                }""";

        assertThat(new String(loggedRequest.getBody()))
                .isEqualTo(requestBody)
                .contains(CHAT_MODEL_ID);

        wireMockServer.verify(
                1,
                postRequestedFor(urlPathEqualTo("/messages"))
                        .withHeader("x-api-key", equalTo(API_KEY))
                        .withHeader("anthropic-version", not(absent()))
                        .withRequestBody(equalToJson(requestBody)));
    }
}
