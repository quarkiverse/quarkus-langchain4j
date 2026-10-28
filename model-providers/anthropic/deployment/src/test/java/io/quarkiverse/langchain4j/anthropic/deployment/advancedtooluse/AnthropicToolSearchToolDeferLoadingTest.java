package io.quarkiverse.langchain4j.anthropic.deployment.advancedtooluse;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkiverse.langchain4j.anthropic.deployment.AnthropicSmokeTest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;

abstract class AnthropicToolSearchToolDeferLoadingTest extends AnthropicSmokeTest {
    abstract String expectedToolName();

    abstract String expectedToolType();

    private static final String EXPECTED_BETA_HEADER = "tools-2024-04-04,advanced-tool-use-2025-11-20";
    @Inject
    TestAiService aiService;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void shouldContainSearchToolAndDeferLoading() throws Exception {
        mock.chat().reply(ChatReply.text("ok").usage(1, 1));

        aiService.chat("Can you tell me the status of order 1002?");

        assertThat(mock.requests()).hasSize(1);

        var loggedRequest = mock.chat().singleRequest();
        assertThat(loggedRequest.path()).isEqualTo("/anthropic/v1/messages");
        assertThat(loggedRequest.header("x-api-key")).isEqualTo(API_KEY);
        assertThat(loggedRequest.header("anthropic-version")).isNotNull();
        assertThat(loggedRequest.header("anthropic-beta")).isEqualTo(EXPECTED_BETA_HEADER);
        var body = loggedRequest.bodyAsJson();
        var tools = body.get("tools");

        var toolSearchTool = findTool(tools, expectedToolName());
        assertThat(toolSearchTool.path("type").asText())
                .isEqualTo(expectedToolType());

        var orderTool = findTool(tools, "get_order_status_by_order_id");
        assertThat(orderTool.get("defer_loading").asBoolean()).isTrue();
        assertThat(orderTool.path("description").asText()).isEqualTo("Get order status by order id");
        assertThat(orderTool.path("input_schema").path("properties").has("orderId")).isTrue();
        assertThat(orderTool.path("input_schema").path("required").toString()).contains("orderId");
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
        @ToolBox(TestOrderService.class)
        String chat(@UserMessage String question);
    }

    @ApplicationScoped
    static class TestOrderService {
        @Tool(name = "get_order_status_by_order_id", value = "Get order status by order id", metadata = "{\"defer_loading\": true}")
        public String getOrderStatus(String orderId) {
            // never called - the mock answers with text, so the tool is not invoked
            return "SHIPPED";
        }
    }
}
