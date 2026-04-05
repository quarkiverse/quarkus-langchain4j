package io.quarkiverse.langchain4j.anthropic.deployment.advancedtooluse;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkiverse.langchain4j.anthropic.deployment.AnthropicSmokeTest;
import io.quarkus.test.QuarkusUnitTest;

class AnthropicProgrammaticToolCallingTest extends AnthropicSmokeTest {
    private static final String MODEL_ID = "claude-sonnet-4-6";
    private static final String EXPECTED_BETA_HEADER = "tools-2024-04-04,advanced-tool-use-2025-11-20";
    private static final String MINIMAL_VALID_ANTHROPIC_RESPONSE = """
            {
              "type": "message",
              "role": "assistant",
              "content": [ { "type": "text", "text": "ok" } ],
              "stop_reason": "end_turn",
              "usage": { "input_tokens": 1, "output_tokens": 1 }
            }
            """;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.chat-model.model-name", MODEL_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.chat-model.programmatic-tool-calling.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.base-url", "http://localhost:%d".formatted(WIREMOCK_PORT));

    @Inject
    TestAiService aiService;

    @Test
    void shouldContainCodeExecutionToolAndAllowedCallers() throws Exception {
        wireMockServer.stubFor(
                post(urlPathEqualTo("/messages"))
                        .withHeader("x-api-key", equalTo(API_KEY))
                        .withHeader("anthropic-version", not(absent()))
                        .withHeader("anthropic-beta", equalTo(EXPECTED_BETA_HEADER))
                        .willReturn(okJson(MINIMAL_VALID_ANTHROPIC_RESPONSE)));

        aiService.chat("What was the average max temperature in Munich in the last 5 days?");

        assertThat(wireMockServer.getAllServeEvents()).hasSize(1);

        var loggedRequest = wireMockServer.getAllServeEvents().get(0).getRequest();
        var body = MAPPER.readTree(loggedRequest.getBodyAsString());
        var tools = body.get("tools");

        var codeExecutionTool = findTool(tools, "code_execution");
        assertThat(codeExecutionTool.path("type").asText())
                .isEqualTo("code_execution_20250825");

        var temperatureTool = findTool(tools, "get_daily_temperatures");
        assertThat(temperatureTool.path("allowed_callers").get(0).asText())
                .isEqualTo("code_execution_20250825");
        assertThat(temperatureTool.path("description").asText())
                .isEqualTo("Get daily temperatures for a city");
    }

    private JsonNode findTool(JsonNode tools, String name) {
        for (JsonNode tool : tools) {
            if (name.equals(tool.path("name").asText())) {
                return tool;
            }
        }
        throw new AssertionError("Tool '" + name + "' not found in tools array");
    }

    @RegisterAiService
    @ApplicationScoped
    interface TestAiService {
        @SystemMessage("You are a helpful assistant. Use tools when needed.")
        @ToolBox(TestTemperatureService.class)
        String chat(@UserMessage String question);
    }

    @ApplicationScoped
    static class TestTemperatureService {
        @Tool(name = "get_daily_temperatures", value = "Get daily temperatures for a city", metadata = "{\"allowed_callers\": [\"code_execution_20250825\"]}")
        public String getDailyTemperatures(String city, int days) {
            return "[{\"min\":0.0,\"max\":5.0}]";
        }
    }
}
