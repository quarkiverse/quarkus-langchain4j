package io.quarkiverse.langchain4j.sample.chatbot;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.service.tool.ToolProvider;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.File;
import java.util.List;
import java.util.function.Supplier;

/**
 * THIS CLASS IS NOT USED!
 *
 * This class just shows how to manually provide a ToolProvider, but in this
 * project, it is unused because we declaratively configure the
 * `ToolProvider` via configuration properties, so this class serves just as
 * a reference example. To use it instead of the generated one, uncomment
 * the @ApplicationScoped annotation here and add a `toolProviderSupplier =
 * FilesystemToolProvider.class` argument to the RegisterAiService
 * annotation on the Bot interface.
 */
//@ApplicationScoped
public class FilesystemToolProvider implements Supplier<ToolProvider> {

    private McpTransport transport;
    private McpClient mcpClient;
    private ToolProvider toolProvider;

    @Override
    public ToolProvider get() {
        if(toolProvider == null) {
            transport = new StdioMcpTransport.Builder()
                    .command(List.of("npm", "exec",
                            "@modelcontextprotocol/server-filesystem@0.6.2",
                            // allowed directory for the server to interact with
                            new File("playground").getAbsolutePath()
                    ))
                    .logEvents(true)
                    .build();
            mcpClient = new DefaultMcpClient.Builder()
                    .transport(transport)
                    .build();
            toolProvider = McpToolProvider.builder()
                    .mcpClients(mcpClient)
                    .build();
        }
        return toolProvider;
    }
}
