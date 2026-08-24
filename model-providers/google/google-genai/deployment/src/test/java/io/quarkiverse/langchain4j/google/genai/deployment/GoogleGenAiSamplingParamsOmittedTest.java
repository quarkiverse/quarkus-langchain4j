package io.quarkiverse.langchain4j.google.genai.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class GoogleGenAiSamplingParamsOmittedTest extends WiremockAware {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.google.genai.base-url", WiremockAware.wiremockUrlForConfig())
            .overrideRuntimeConfigKey("quarkus.langchain4j.google.genai.api-key", "dummy");

    @Inject
    ChatModel chatModel;

    @Test
    void temperatureIsNotSentWhenUnconfigured() {
        stubGenerateContent();

        chatModel.chat("hello");

        String requestBody = new String(requestBodyOfSingleRequest());
        assertThat(requestBody).doesNotContain("\"temperature\"");
    }

    private void stubGenerateContent() {
        wiremock().register(post(urlMatching(".*:generateContent.*"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "candidates": [
                                    {
                                      "content": { "role": "model", "parts": [ { "text": "hi" } ] },
                                      "finishReason": "STOP"
                                    }
                                  ],
                                  "usageMetadata": {
                                    "promptTokenCount": 1,
                                    "candidatesTokenCount": 2,
                                    "totalTokenCount": 3
                                  }
                                }
                                """)));
    }
}
