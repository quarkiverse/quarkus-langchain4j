package io.quarkiverse.langchain4j.mcp.runtime.apicurio;

/**
 * Connection metadata for an MCP server, extracted from Apicurio Registry artifact labels.
 * <p>
 * The artifact content itself follows the official MCP spec (name, inputSchema, etc.)
 * and is validated by the registry. Connection details are stored as artifact labels:
 * <ul>
 * <li>{@code mcp-server-url} — the URL of the MCP server</li>
 * <li>{@code mcp-transport-type} — the transport type (e.g., "streamable-http")</li>
 * </ul>
 */
public record McpServerDefinition(
        String url,
        String transportType) {
}
