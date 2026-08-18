package io.quarkiverse.langchain4j.anthropic.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.not;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkus.test.QuarkusUnitTest;

class AnthropicSamplingParametersUnsetTest extends AnthropicSmokeTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.base-url", "http://localhost:%d".formatted(WIREMOCK_PORT));

    @Inject
    ChatModel chatModel;

    @Test
    void shouldOmitUnsetSamplingParameters() throws Exception {
        wireMockServer.stubFor(
                post(urlPathEqualTo("/messages"))
                        .withHeader("x-api-key", equalTo(API_KEY))
                        .withHeader("anthropic-version", not(absent()))
                        .willReturn(okJson("""
                                {
                                  "type": "message",
                                  "role": "assistant",
                                  "content": [{"type": "text", "text": "ok"}],
                                  "stop_reason": "end_turn",
                                  "usage": {"input_tokens": 1, "output_tokens": 1}
                                }
                                """)));

        chatModel.chat("Hello");

        assertThat(wireMockServer.getAllServeEvents()).hasSize(1);
        JsonNode request = MAPPER.readTree(wireMockServer.getAllServeEvents().get(0).getRequest().getBodyAsString());
        assertThat(request.get("top_k")).isNull();
        assertThat(request.get("top_p")).isNull();
        assertThat(request.get("temperature")).isNull();
    }
}
