package io.quarkiverse.langchain4j.openai.test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Map;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class ResponsesChatModelSmokeTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.openai.chat-model.mode", "responses")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "my-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.responses.store", "false")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.responses.max-output-tokens", "100")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @Inject
    ChatModel chatModel;

    @BeforeEach
    void reset() {
        resetRequests();
    }

    @Test
    void blockingChatTargetsResponsesEndpoint() throws IOException {
        stubResponsesEndpoint("Hello from Responses API!");

        String response = chatModel.chat("hello");
        assertThat(response).isEqualTo("Hello from Responses API!");

        Map<String, Object> requestBody = getRequestAsMap();
        assertThat(requestBody)
                .containsEntry("model", "gpt-4o-mini")
                .containsEntry("store", false)
                .containsEntry("max_output_tokens", 100)
                .containsKey("input");

        wiremock().verifyThat(postRequestedFor(urlEqualTo("/v1/responses"))
                .withHeader("Authorization", equalTo("Bearer my-key")));
    }

    private void stubResponsesEndpoint(String text) {
        wiremock().register(post(urlEqualTo("/v1/responses"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                """
                                        {
                                          "id": "resp_123",
                                          "model": "gpt-4o-mini",
                                          "status": "completed",
                                          "output": [
                                            {
                                              "type": "message",
                                              "role": "assistant",
                                              "content": [ { "type": "output_text", "text": "%s" } ]
                                            }
                                          ],
                                          "usage": { "input_tokens": 1, "output_tokens": 2, "total_tokens": 3 }
                                        }
                                        """
                                        .formatted(text))));
    }
}
