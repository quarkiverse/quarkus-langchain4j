package io.quarkiverse.langchain4j.anthropic.deployment.advancedtooluse;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkiverse.langchain4j.anthropic.deployment.AnthropicSmokeTest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

class AnthropicProgrammaticToolCallingTest extends AnthropicSmokeTest {
    private static final String MODEL_ID = "claude-sonnet-4-6";
    private static final String EXPECTED_BETA_HEADER = "tools-2024-04-04,advanced-tool-use-2025-11-20";
    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.chat-model.model-name", MODEL_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.chat-model.programmatic-tool-calling.enabled", "true");

    @Inject
    TestAiService aiService;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void shouldContainCodeExecutionToolAndAllowedCallers() throws Exception {
        mock.chat().reply(ChatReply.text("ok").usage(1, 1));

        aiService.chat("What was the average max temperature in Munich in the last 5 days?");

        assertThat(mock.requests()).hasSize(1);

        var loggedRequest = mock.chat().singleRequest();
        assertThat(loggedRequest.path()).isEqualTo("/anthropic/v1/messages");
        assertThat(loggedRequest.header("x-api-key")).isEqualTo(API_KEY);
        assertThat(loggedRequest.header("anthropic-version")).isNotNull();
        assertThat(loggedRequest.header("anthropic-beta")).isEqualTo(EXPECTED_BETA_HEADER);
        var body = loggedRequest.bodyAsJson();
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
