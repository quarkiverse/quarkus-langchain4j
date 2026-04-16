package io.quarkiverse.langchain4j.mcp.runtime;

import java.util.function.BiConsumer;

import dev.langchain4j.mcp.client.McpClient;
import io.quarkus.arc.Arc;

/**
 * A handler that fires a {@link McpResourceUpdatedEvent} as a CDI event when an MCP server
 * sends a {@code notifications/resources/updated} notification for a subscribed resource.
 * <p>
 * This handler is automatically attached to managed (declarative) MCP clients when the application
 * contains at least one CDI observer method for {@link McpResourceUpdatedEvent}. If you create
 * an {@link dev.langchain4j.mcp.client.DefaultMcpClient} programmatically and want to receive
 * resource update notifications as CDI events, pass an instance of this class to the
 * {@link dev.langchain4j.mcp.client.DefaultMcpClient.Builder#onResourceUpdated(BiConsumer)} method.
 */
public class McpResourceUpdatedHandler implements BiConsumer<McpClient, String> {

    @Override
    public void accept(McpClient mcpClient, String uri) {
        String key = mcpClient.key();
        Arc.container().beanManager().getEvent().select(McpResourceUpdatedEvent.class,
                McpClientName.Literal.of(key)).fire(new McpResourceUpdatedEvent(uri, key));
    }
}
