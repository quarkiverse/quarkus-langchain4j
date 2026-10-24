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

class AnthropicAllAdvancedToolFeaturesTest extends AnthropicSmokeTest {

    private static final String MODEL_ID = "claude-sonnet-4-6";

    private static final String EXPECTED_BETA_HEADER = "tools-2024-04-04,advanced-tool-use-2025-11-20";

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.chat-model.model-name", MODEL_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.chat-model.tool-search.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.chat-model.programmatic-tool-calling.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.chat-model.tool-use-examples.enabled", "true");

    @Inject
    TestAiService aiService;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void shouldContainAllAdvancedToolFeaturesWithDeduplicatedBetaHeader() throws Exception {
        mock.chat().reply(ChatReply.text("ok").usage(1, 1));

        aiService.chat("Create a critical ticket for the login page failure");

        assertThat(mock.requests()).hasSize(1);

        var loggedRequest = mock.chat().singleRequest();
        assertThat(loggedRequest.path()).isEqualTo("/anthropic/v1/messages");
        assertThat(loggedRequest.header("x-api-key")).isEqualTo(API_KEY);
        assertThat(loggedRequest.header("anthropic-version")).isNotNull();
        assertThat(loggedRequest.header("anthropic-beta")).isEqualTo(EXPECTED_BETA_HEADER);
        var body = loggedRequest.bodyAsJson();
        var tools = body.get("tools");

        var betaHeader = loggedRequest.header("anthropic-beta");
        var advancedToolUseBetaCount = betaHeader.split("advanced-tool-use-2025-11-20", -1).length - 1;
        assertThat(advancedToolUseBetaCount)
                .as("advanced-tool-use beta should appear exactly once despite three features requiring it")
                .isEqualTo(1);

        System.out.println(tools);

        var toolSearchTool = findTool(tools, "tool_search_tool_regex");
        assertThat(toolSearchTool.path("type").asText())
                .isEqualTo("tool_search_tool_regex_20251119");

        var codeExecutionTool = findTool(tools, "code_execution");
        assertThat(codeExecutionTool.path("type").asText())
                .isEqualTo("code_execution_20250825");

        var createTicketTool = findTool(tools, "create_ticket");

        assertThat(createTicketTool.path("defer_loading").asBoolean())
                .isTrue();

        assertThat(createTicketTool.path("allowed_callers").get(0).asText())
                .isEqualTo("code_execution_20250825");

        var inputExamples = createTicketTool.get("input_examples");
        assertThat(inputExamples).isNotNull();
        assertThat(inputExamples.isArray()).isTrue();
        assertThat(inputExamples.size()).isEqualTo(3);

        assertThat(inputExamples.get(0).path("due_date").asText()).isEqualTo("2024-11-06");
        assertThat(inputExamples.get(1).path("due_date").asText()).isEqualTo("2024-12-01");

        assertThat(inputExamples.get(2).has("due_date")).isFalse();
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
        @ToolBox(TestTicketService.class)
        String chat(@UserMessage String question);
    }

    @ApplicationScoped
    static class TestTicketService {
        private static final String METADATA = """
                {
                  "defer_loading": true,
                  "allowed_callers": ["code_execution_20250825"],
                  "input_examples": [
                    {
                      "title": "Login page returns 500 error",
                      "priority": "critical",
                      "labels": ["bug", "authentication"],
                      "due_date": "2024-11-06"
                    },
                    {
                      "title": "Update API documentation",
                      "due_date": "2024-12-01"
                    },
                    {
                      "title": "Brainstorming session"
                    }
                  ]
                }
                """;

        @Tool(name = "create_ticket", value = "Create a support ticket", metadata = METADATA)
        public String createTicket(String title, String priority, String dueDate) {
            return "TICKET-123";
        }
    }
}
