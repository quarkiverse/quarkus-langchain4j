package io.quarkiverse.langchain4j.anthropic.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.not;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusUnitTest;

class ModelAuthProviderSmokeTest extends AnthropicSmokeTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.base-url",
                    "http://localhost:%d".formatted(WIREMOCK_PORT));

    @Inject
    ChatModel chatModel;

    @Inject
    StreamingChatModel streamingChatModel;

    @Test
    void testModelAuthProviderUsed() {
        assertThat(ClientProxy.unwrap(chatModel)).isInstanceOf(AnthropicChatModel.class);
        assertThat(ClientProxy.unwrap(streamingChatModel)).isInstanceOf(AnthropicStreamingChatModel.class);

        wireMockServer.stubFor(
                post(urlPathEqualTo("/messages"))
                        .withHeader("Authorization", equalTo("Bearer token"))
                        .withHeader("x-api-key", absent())
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

        String response = chatModel.chat("hello");
        assertThat(response).isEqualTo("Hello!");

        assertThat(wireMockServer.getAllServeEvents()).hasSize(1);

        var loggedRequest = wireMockServer.getAllServeEvents().get(0).getRequest();
        assertThat(loggedRequest.getHeader("Authorization")).isEqualTo("Bearer token");
        assertThat(loggedRequest.getHeader("x-api-key")).isNull();
    }

    @ApplicationScoped
    public static class DummyModelAuthProvider implements ModelAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            return "Bearer token";
        }
    }
}
