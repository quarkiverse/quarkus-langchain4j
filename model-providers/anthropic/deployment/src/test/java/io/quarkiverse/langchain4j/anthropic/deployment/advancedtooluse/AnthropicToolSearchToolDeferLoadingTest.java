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

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkiverse.langchain4j.anthropic.deployment.AnthropicSmokeTest;

abstract class AnthropicToolSearchToolDeferLoadingTest extends AnthropicSmokeTest {
    abstract String expectedToolName();

    abstract String expectedToolType();

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

    @Inject
    TestAiService aiService;

    @Test
    void shouldContainSearchToolAndDeferLoading() throws Exception {
        wireMockServer.stubFor(
                post(urlPathEqualTo("/messages"))
                        .withHeader("x-api-key", equalTo(API_KEY))
                        .withHeader("anthropic-version", not(absent()))
                        .withHeader("anthropic-beta", equalTo(EXPECTED_BETA_HEADER))
                        .willReturn(okJson(MINIMAL_VALID_ANTHROPIC_RESPONSE)));

        aiService.chat("Can you tell me the status of order 1002?");

        assertThat(wireMockServer.getAllServeEvents()).hasSize(1);

        var loggedRequest = wireMockServer.getAllServeEvents().get(0).getRequest();
        var body = MAPPER.readTree(loggedRequest.getBodyAsString());
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
            // never called - WireMock intercepts before tool invocation
            return "SHIPPED";
        }
    }
}
