package io.quarkiverse.langchain4j.openai.test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
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

public class OpenAiResponsesApiConfigTest extends OpenAiBaseTest {

    private static final String RESPONSES_PATH = "/v1/responses";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.api", "responses")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.model-name", "gpt-4.1-mini")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.max-output-tokens", "512")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.store", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.previous-response-id", "resp_prev")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.truncation", "auto")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.include", "reasoning.encrypted_content")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.reasoning-effort", "low")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.reasoning-summary", "auto")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.text-verbosity", "medium")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.prompt-cache-key", "cache-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.prompt-cache-retention", "24h")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.top-logprobs", "3")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.parallel-tool-calls", "false")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.max-tool-calls", "2")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.safety-identifier", "user-123")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.service-tier", "default");

    @Inject
    ChatModel chatModel;

    @BeforeEach
    void reset() {
        resetRequests();
        wiremock().resetMappings();
    }

    @Test
    void mapsResponsesSpecificConfigToRequestPayload() throws IOException {
        wiremock().register(
                post(urlEqualTo(RESPONSES_PATH))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                        {
                                          "id": "resp_config",
                                          "object": "response",
                                          "model": "gpt-4.1-mini",
                                          "status": "completed",
                                          "output": [
                                            {
                                              "type": "message",
                                              "content": [
                                                {
                                                  "type": "output_text",
                                                  "text": "ok"
                                                }
                                              ]
                                            }
                                          ]
                                        }
                                        """)));

        assertThat(chatModel.chat("hello")).isEqualTo("ok");

        Map<String, Object> requestAsMap = getRequestAsMap();
        assertThat(requestAsMap)
                .containsEntry("model", "gpt-4.1-mini")
                .containsEntry("max_output_tokens", 512)
                .containsEntry("store", true)
                .containsEntry("previous_response_id", "resp_prev")
                .containsEntry("truncation", "auto")
                .containsEntry("reasoning_effort", "low")
                .containsEntry("reasoning_summary", "auto")
                .containsEntry("text_verbosity", "medium")
                .containsEntry("prompt_cache_key", "cache-key")
                .containsEntry("prompt_cache_retention", "24h")
                .containsEntry("top_logprobs", 3)
                .containsEntry("parallel_tool_calls", false)
                .containsEntry("max_tool_calls", 2)
                .containsEntry("safety_identifier", "user-123")
                .containsEntry("service_tier", "default")
                .containsEntry("include", List.of("reasoning.encrypted_content"));
    }
}
