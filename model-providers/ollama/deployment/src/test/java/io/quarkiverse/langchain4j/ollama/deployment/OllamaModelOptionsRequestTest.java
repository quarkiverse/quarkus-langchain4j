package io.quarkiverse.langchain4j.ollama.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class OllamaModelOptionsRequestTest extends WiremockAware {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.ollama.base-url", WiremockAware.wiremockUrlForConfig())
            .overrideConfigKey("quarkus.langchain4j.ollama.chat-model.model-name", "qwen3:1.7b")
            .overrideConfigKey("quarkus.langchain4j.ollama.chat-model.model-options.think", "true")
            .overrideConfigKey("quarkus.langchain4j.devservices.enabled", "false");

    @Inject
    ChatModel chatModel;

    @Test
    void shouldSendThinkOptionInRequest() {
        wiremock().register(
                post(urlEqualTo("/api/chat"))
                        .withRequestBody(matchingJsonPath("$.model", equalTo("qwen3:1.7b")))
                        .withRequestBody(matchingJsonPath("$.think", equalTo("true")))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                        {
                                          "model": "qwen3:1.7b",
                                          "created_at": "2024-12-11T15:21:23.422542932Z",
                                          "message": {
                                            "role": "assistant",
                                            "content": "Hello!"
                                          },
                                          "done_reason": "stop",
                                          "done": true
                                        }
                                        """)));

        String response = chatModel.chat("Say hello");
        assertThat(response).isEqualTo("Hello!");
    }
}
