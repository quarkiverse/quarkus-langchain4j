package io.quarkiverse.langchain4j.mcp.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.service.tool.ToolProvider;
import io.quarkiverse.langchain4j.mcp.runtime.config.McpClientConfig;
import io.quarkiverse.langchain4j.mcp.runtime.config.McpConfiguration;
import io.quarkiverse.langchain4j.mcp.runtime.http.QuarkusHttpMcpTransport;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.runtime.configuration.ConfigurationException;

@Recorder
public class McpRecorder {

    public Supplier<McpClient> mcpClientSupplier(String key, McpConfiguration configuration) {
        return new Supplier<McpClient>() {
            @Override
            public McpClient get() {
                McpTransport transport = null;
                McpClientConfig config = configuration.clients().get(key);
                switch (config.transportType()) {
                    case STDIO:
                        List<String> command = config.command().orElseThrow(() -> new ConfigurationException(
                                "MCP client configuration named " + key + " is missing the 'command' property"));
                        transport = new StdioMcpTransport.Builder()
                                .command(command)
                                .logEvents(config.logResponses().orElse(false))
                                .environment(config.environment())
                                .build();
                        break;
                    case HTTP:
                        transport = new QuarkusHttpMcpTransport.Builder()
                                .sseUrl(config.url().orElseThrow(() -> new ConfigurationException(
                                        "MCP client configuration named " + key + " is missing the 'url' property")))
                                .logRequests(config.logRequests().orElse(false))
                                .logResponses(config.logResponses().orElse(false))
                                .build();
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown transport type: " + config.transportType());
                }
                return new DefaultMcpClient.Builder()
                        .transport(transport)
                        .toolExecutionTimeout(config.toolExecutionTimeout())
                        // TODO: it should be possible to choose a log handler class via configuration
                        .logHandler(new QuarkusDefaultMcpLogHandler(key))
                        .build();
            }
        };
    }

    public Function<SyntheticCreationalContext<ToolProvider>, ToolProvider> toolProviderFunction(
            Set<String> mcpClientNames) {
        return new Function<>() {
            @Override
            public ToolProvider apply(SyntheticCreationalContext<ToolProvider> context) {
                List<McpClient> clients = new ArrayList<>();
                for (String mcpClientName : mcpClientNames) {
                    McpClientName.Literal qualifier = McpClientName.Literal.of(mcpClientName);
                    clients.add(context.getInjectedReference(McpClient.class, qualifier));
                }
                return new McpToolProvider.Builder()
                        .mcpClients(clients)
                        .build();
            }

        };
    }
}
