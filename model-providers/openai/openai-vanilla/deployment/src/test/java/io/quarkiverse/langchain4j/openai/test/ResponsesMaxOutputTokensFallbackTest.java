package io.quarkiverse.langchain4j.openai.test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * When {@code responses.max-output-tokens} is not set, the chat-completions limits keep applying on the Responses API:
 * {@code max-completion-tokens} first, then the deprecated {@code max-tokens}.
 */
public class ResponsesMaxOutputTokensFallbackTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.from-completion.chat-model.mode", "responses")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.from-completion.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.from-completion.chat-model.max-completion-tokens", "50")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.from-completion.chat-model.max-tokens", "40")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.from-max-tokens.chat-model.mode", "responses")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.from-max-tokens.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.from-max-tokens.chat-model.max-tokens", "40")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.explicit.chat-model.mode", "responses")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.explicit.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.explicit.chat-model.responses.max-output-tokens", "100")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.explicit.chat-model.max-completion-tokens", "50");

    @Inject
    @ModelName("from-completion")
    ChatModel fromCompletionModel;

    @Inject
    @ModelName("from-max-tokens")
    ChatModel fromMaxTokensModel;

    @Inject
    @ModelName("explicit")
    ChatModel explicitModel;

    @BeforeEach
    void setUp() {
        resetMappings();
        resetRequests();
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
                                      "content": [ { "type": "output_text", "text": "hello" } ]
                                    }
                                  ],
                                  "usage": { "input_tokens": 1, "output_tokens": 2, "total_tokens": 3 }
                                }
                                """)));
    }

    @Test
    void maxCompletionTokensWinsOverMaxTokens() throws IOException {
        fromCompletionModel.chat("hi");
        assertThat(getRequestAsMap()).containsEntry("max_output_tokens", 50);
    }

    @Test
    void maxTokensIsUsedAsLastResort() throws IOException {
        fromMaxTokensModel.chat("hi");
        assertThat(getRequestAsMap()).containsEntry("max_output_tokens", 40);
    }

    @Test
    void explicitMaxOutputTokensWins() throws IOException {
        explicitModel.chat("hi");
        assertThat(getRequestAsMap()).containsEntry("max_output_tokens", 100);
    }
}
