package io.quarkiverse.langchain4j.mcp.runtime;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import io.quarkiverse.langchain4j.runtime.aiservice.QuarkusToolProviderRequest;

public class QuarkusMcpToolProvider implements ToolProvider {

    private static final Logger log = LoggerFactory.getLogger(QuarkusMcpToolProvider.class);

    private final Map<String, McpClient> mcpClients;
    private final boolean failIfOneServerFails;

    QuarkusMcpToolProvider(Map<String, McpClient> mcpClients) {
        this(mcpClients, false);
    }

    QuarkusMcpToolProvider(Map<String, McpClient> mcpClients, boolean failIfOneServerFails) {
        this.mcpClients = mcpClients;
        this.failIfOneServerFails = failIfOneServerFails;
    }

    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        ToolProviderResult.Builder builder = ToolProviderResult.builder();

        for (McpClient mcpClient : getMcpClients(request)) {
            try {
                for (ToolSpecification toolSpecification : mcpClient.listTools()) {
                    builder.add(toolSpecification, (executionRequest, memoryId) -> mcpClient.executeTool(executionRequest));
                }
            } catch (Exception e) {
                if (this.failIfOneServerFails) {
                    throw new RuntimeException("Failed to retrieve tools from MCP server", e);
                }

                log.warn("Failed to retrieve tools from MCP server", e);
            }
        }

        return builder.build();

    }

    private Collection<McpClient> getMcpClients(ToolProviderRequest request) {
        if (request instanceof QuarkusToolProviderRequest quarkusRequest) {
            List<String> mcpClientNames = quarkusRequest.getMcpClientNames();
            if (mcpClientNames == null) {
                return Collections.emptyList();
            }
            if (mcpClientNames.isEmpty()) {
                return mcpClients.values();
            }
            return mcpClientNames.stream()
                    .map(mcpClientName -> {
                        McpClient mcpClient = mcpClients.get(mcpClientName);
                        if (mcpClient == null) {
                            log.warn("Failed to find MCP client for server {}", mcpClientName);
                        }
                        return mcpClient;
                    })
                    .filter(Objects::nonNull)
                    .toList();
        }

        return mcpClients.values();
    }
}
