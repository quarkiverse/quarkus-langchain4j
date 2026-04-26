package io.quarkiverse.langchain4j.mcp.runtime;

/**
 * CDI event fired when an MCP server sends a {@code notifications/resources/updated}
 * notification for a subscribed resource. Use a CDI observer method qualified with
 * {@link McpClientName} to receive this event for a specific MCP client.
 */
public class McpResourceUpdatedEvent {

    private final String uri;
    private final String mcpClientKey;

    public McpResourceUpdatedEvent(String uri, String mcpClientKey) {
        this.uri = uri;
        this.mcpClientKey = mcpClientKey;
    }

    /**
     * The URI of the updated resource.
     */
    public String uri() {
        return uri;
    }

    /**
     * The key (name) of the MCP client that received the notification.
     */
    public String mcpClientKey() {
        return mcpClientKey;
    }
}
