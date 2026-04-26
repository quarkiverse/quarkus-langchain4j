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

class AnthropicToolUseExamplesTest extends AnthropicSmokeTest {
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
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.chat-model.tool-use-examples.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.base-url", "http://localhost:%d".formatted(WIREMOCK_PORT));

    @Inject
    TestAiService aiService;

    @Test
    void shouldContainInputExamplesInToolDefinition() throws Exception {
        wireMockServer.stubFor(
                post(urlPathEqualTo("/messages"))
                        .withHeader("x-api-key", equalTo(API_KEY))
                        .withHeader("anthropic-version", not(absent()))
                        .withHeader("anthropic-beta", equalTo(EXPECTED_BETA_HEADER))
                        .willReturn(okJson(MINIMAL_VALID_ANTHROPIC_RESPONSE)));

        aiService.chat("Create a critical bug ticket for the login page");

        assertThat(wireMockServer.getAllServeEvents()).hasSize(1);

        var loggedRequest = wireMockServer.getAllServeEvents().get(0).getRequest();
        var body = MAPPER.readTree(loggedRequest.getBodyAsString());
        var tools = body.get("tools");

        var createTicketTool = findTool(tools, "create_ticket");
        var inputExamples = createTicketTool.get("input_examples");

        assertThat(inputExamples).isNotNull();
        assertThat(inputExamples.isArray()).isTrue();
        assertThat(inputExamples.size()).isEqualTo(3);

        assertThat(inputExamples.get(0).path("title").asText())
                .isEqualTo("Login page returns 500 error");
        assertThat(inputExamples.get(0).path("priority").asText())
                .isEqualTo("critical");
        assertThat(inputExamples.get(0).path("date").asText())
                .isEqualTo("2024-11-06");

        assertThat(inputExamples.get(1).path("title").asText())
                .isEqualTo("Update API documentation");
        assertThat(inputExamples.get(1).has("priority")).isFalse();
        assertThat(inputExamples.get(1).path("date").asText())
                .isEqualTo("2024-12-01");

        assertThat(inputExamples.get(2).path("title").asText())
                .isEqualTo("Brainstorming session");
        assertThat(inputExamples.get(2).has("date")).isFalse();
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
                  "input_examples": [
                    {
                      "title": "Login page returns 500 error",
                      "priority": "critical",
                      "labels": ["bug", "authentication"],
                      "date": "2024-11-06"
                    },
                    {
                      "title": "Update API documentation",
                      "date": "2024-12-01"
                    },
                    {
                      "title": "Brainstorming session"
                    }
                  ]
                }
                """;

        @Tool(name = "create_ticket", value = "Create a support ticket", metadata = METADATA)
        public String createTicket(String title, String priority, String date) {
            return "TICKET-123";
        }
    }
}
