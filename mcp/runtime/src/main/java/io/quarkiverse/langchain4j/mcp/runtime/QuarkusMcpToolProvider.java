package io.quarkiverse.langchain4j.mcp.runtime;

import java.util.List;
import java.util.function.BiPredicate;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import io.quarkiverse.langchain4j.runtime.aiservice.QuarkusToolProviderRequest;

public class QuarkusMcpToolProvider extends McpToolProvider {

    QuarkusMcpToolProvider(List<McpClient> mcpClients) {
        super(mcpClients, false, (mcp, tool) -> true);
    }

    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        return provideTools(request, getMcpClientsFilter(request));
    }

    private BiPredicate<McpClient, ToolSpecification> getMcpClientsFilter(ToolProviderRequest request) {
        if (request instanceof QuarkusToolProviderRequest quarkusRequest) {
            return new McpClientKeyFilter(quarkusRequest.getMcpClientNames());
        }
        return (mcp, tool) -> true;
    }

    private static class McpClientKeyFilter implements BiPredicate<McpClient, ToolSpecification> {
        private final List<String> keys;

        private McpClientKeyFilter(List<String> keys) {
            this.keys = keys;
        }

        @Override
        public boolean test(McpClient mcpClient, ToolSpecification tool) {
            // keys == null means no McpToolBox annotation, so no MCP clients, whereas
            // keys.size() == 0 means all MCP clients
            return keys != null
                    && (keys.isEmpty() || keys.stream().anyMatch(name -> name.equals(mcpClient.key())));
        }
    }
}
