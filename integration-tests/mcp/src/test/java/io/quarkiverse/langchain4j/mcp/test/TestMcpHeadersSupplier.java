package io.quarkiverse.langchain4j.mcp.test;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.mcp.client.McpCallContext;
import dev.langchain4j.mcp.client.McpHeadersSupplier;
import dev.langchain4j.mcp.protocol.McpCallToolRequest;

@ApplicationScoped
public class TestMcpHeadersSupplier implements McpHeadersSupplier {

    @Override
    public Map<String, String> apply(McpCallContext context) {
        if (context != null && context.message() instanceof McpCallToolRequest toolRequest) {
            String toolName = (String) toolRequest.getParams().get("name");
            return Map.of("X-Tool-Name", toolName);
        }
        return Map.of();
    }

}
