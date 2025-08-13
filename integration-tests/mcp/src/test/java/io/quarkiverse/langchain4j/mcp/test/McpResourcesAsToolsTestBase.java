package io.quarkiverse.langchain4j.mcp.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.node.ArrayNode;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.internal.Json;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.resourcesastools.DefaultMcpResourcesAsToolsPresenter;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;

public abstract class McpResourcesAsToolsTestBase {

    @Inject
    @McpClientName("alice")
    McpClient mcpClientAlice;

    @Inject
    @McpClientName("bob")
    McpClient mcpClientBob;

    @Inject
    ChatModel chatModel;

    @Inject
    ChatService service;

    @Test
    public void listResourcesAndThenGet() {
        ToolProvider toolProvider = McpToolProvider.builder()
                .mcpClients(mcpClientAlice)
                .resourcesAsToolsPresenter(
                        DefaultMcpResourcesAsToolsPresenter.builder().build())
                .build();

        // check that the tool provider has two tools: list_resources and get_resource
        ToolProviderResult toolProviderResult = toolProvider.provideTools(null);
        assertThat(toolProviderResult.tools()).hasSize(2);
        List<String> toolNames = toolProviderResult.tools().keySet().stream()
                .map(ToolSpecification::name)
                .collect(Collectors.toList());
        assertThat(toolNames).containsExactlyInAnyOrder("list_resources", "get_resource");

        // call the list_resources tool and verify the output
        String listResourcesResult = toolProviderResult.toolExecutorByName("list_resources").execute(null, null);
        ArrayNode resources = Json.fromJson(listResourcesResult, ArrayNode.class);
        assertThat(resources.size()).isEqualTo(1);
        assertThat(resources.get(0).get("mcpServer").asText()).isEqualTo("alice");
        assertThat(resources.get(0).get("uri").asText()).isEqualTo("file:///info");
        assertThat(resources.get(0).get("uriTemplate").isNull()).isTrue();
        assertThat(resources.get(0).get("name").asText()).isEqualTo("basicInfo");
        assertThat(resources.get(0).get("description").asText()).isEqualTo("Basic information about Alice");
        assertThat(resources.get(0).get("mimeType").asText()).isEqualTo("text/plain");

        // call the get_resource tool
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("get_resource")
                .arguments("{\"mcpServer\": \"alice\", \"uri\": \"file:///info\"}")
                .build();
        String getBasicInfoResult = toolProviderResult.toolExecutorByName("get_resource").execute(request, null);
        assertThat(getBasicInfoResult).isEqualTo("Alice was born in 1962 and lives in Manchester.");
    }

    @Test
    public void integrationTestWithAiService() {
        String aliceResponse = service.chat("When was Alice born?");
        assertThat(aliceResponse).contains("1962");

        String bobResponse = service.chat("When was Bob born?");
        assertThat(bobResponse).contains("1956");
    }

    @RegisterAiService
    @ApplicationScoped
    interface ChatService {
        @SystemMessage("Use list_resources and get_resource tools to answer user's questions")
        String chat(String prompt);
    }
}
