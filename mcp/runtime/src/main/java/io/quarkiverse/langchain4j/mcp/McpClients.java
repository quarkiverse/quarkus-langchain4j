package io.quarkiverse.langchain4j.mcp;

import java.util.Map;

import dev.langchain4j.mcp.client.McpClient;

/**
 * Provides access to all MCP clients that are known from the Quarkus configuration model.
 */
public class McpClients {

    private final Map<String, McpClient> clients;

    public McpClients(Map<String, McpClient> clients) {
        this.clients = clients;
    }

    public Map<String, McpClient> listAll() {
        return clients;
    }

    public McpClient get(String clientName) {
        return clients.get(clientName);
    }

}
