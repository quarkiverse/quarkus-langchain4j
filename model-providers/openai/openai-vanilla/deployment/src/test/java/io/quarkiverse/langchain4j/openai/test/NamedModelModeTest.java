package io.quarkiverse.langchain4j.openai.test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

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
import io.quarkus.test.QuarkusUnitTest;

public class NamedModelModeTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            // "completion" model uses the default chat-completion mode
            .overrideConfigKey("quarkus.langchain4j.openai.completion.chat-model.mode", "chat-completion")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.completion.api-key", "key-completion")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.completion.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            // "responses" model uses the responses mode
            .overrideConfigKey("quarkus.langchain4j.openai.responses.chat-model.mode", "responses")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.responses.api-key", "key-responses")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.responses.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @Inject
    @ModelName("completion")
    ChatModel completionModel;

    @Inject
    @ModelName("responses")
    ChatModel responsesModel;

    @BeforeEach
    void setup() {
        setChatCompletionMessageContent("Hello from Chat Completions!");
        resetRequests();
        resetMappings();
        stubChatCompletionEndpoint();
        stubResponsesEndpoint();
    }

    @Test
    void completionModelTargetsChatCompletionsEndpoint() {
        String result = completionModel.chat("hi");
        assertThat(result).isEqualTo("Hello from Chat Completions!");

        wiremock().verifyThat(1, postRequestedFor(urlEqualTo("/v1/chat/completions")));
        wiremock().verifyThat(0, postRequestedFor(urlEqualTo("/v1/responses")));
    }

    @Test
    void responsesModelTargetsResponsesEndpoint() {
        String result = responsesModel.chat("hi");
        assertThat(result).isEqualTo("Hello from Responses API!");

        wiremock().verifyThat(0, postRequestedFor(urlEqualTo("/v1/chat/completions")));
        wiremock().verifyThat(1, postRequestedFor(urlEqualTo("/v1/responses")));
    }

    private void stubChatCompletionEndpoint() {
        wiremock().register(post(urlEqualTo("/v1/chat/completions"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "chatcmpl-123",
                                  "object": "chat.completion",
                                  "choices": [
                                    {
                                      "index": 0,
                                      "message": {
                                        "role": "assistant",
                                        "content": "Hello from Chat Completions!"
                                      },
                                      "finish_reason": "stop"
                                    }
                                  ],
                                  "usage": { "prompt_tokens": 1, "completion_tokens": 2, "total_tokens": 3 }
                                }
                                """)));
    }

    private void stubResponsesEndpoint() {
        wiremock().register(post(urlEqualTo("/v1/responses"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "resp_123",
                                  "model": "gpt-4o-mini",
                                  "status": "completed",
                                  "output": [
                                    {
                                      "type": "message",
                                      "role": "assistant",
                                      "content": [ { "type": "output_text", "text": "Hello from Responses API!" } ]
                                    }
                                  ],
                                  "usage": { "input_tokens": 1, "output_tokens": 2, "total_tokens": 3 }
                                }
                                """)));
    }
}
