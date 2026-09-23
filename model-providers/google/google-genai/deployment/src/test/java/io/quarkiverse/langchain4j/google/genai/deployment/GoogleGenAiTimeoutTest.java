package io.quarkiverse.langchain4j.google.genai.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
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

public class GoogleGenAiTimeoutTest extends WiremockAware {

    private static final String CHAT_MODEL_ID = "gemini-2.5-flash";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.google.genai.base-url", WiremockAware.wiremockUrlForConfig())
            .overrideRuntimeConfigKey("quarkus.langchain4j.google.genai.api-key", "dummy")
            .overrideRuntimeConfigKey("quarkus.langchain4j.google.genai.timeout", "15s");

    @Inject
    ChatModel chatModel;

    @Test
    void honoursConfiguredTimeoutBeyondOkHttpDefault() {
        wiremock().register(
                post(urlEqualTo(String.format("/v1beta/models/%s:generateContent", CHAT_MODEL_ID)))
                        .willReturn(aResponse()
                                .withFixedDelay(11_000)
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                        {
                                          "candidates": [
                                            {
                                              "content": { "role": "model", "parts": [ { "text": "Slow but fine" } ] },
                                              "finishReason": "STOP"
                                            }
                                          ]
                                        }
                                        """)));

        assertThat(chatModel.chat("hello")).isEqualTo("Slow but fine");
    }
}
