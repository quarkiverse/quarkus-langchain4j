package io.quarkiverse.langchain4j.mcp.runtime;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.mcp.client.McpToolProviderRequest;

import java.util.List;

public class McpToolProviderRequestFactory {

    static McpToolProviderRequest create(Object memoryId, UserMessage userMessage, List<String> mcpClientKeys) {
        return McpToolProviderRequest.builder()
                .invocationContext(InvocationContext.builder().chatMemoryId(memoryId).build())
                .userMessage(userMessage)
                // keys == null means no McpToolBox annotation, so no MCP clients, whereas
                // keys.size() == 0 means all MCP clients
                .toolFilter((mcpClient, toolSpecification) ->
                        mcpClientKeys != null && (mcpClientKeys.isEmpty() || mcpClientKeys.stream().anyMatch(name -> name.equals(mcpClient.key()))))
                .build();
    }
}
