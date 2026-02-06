package io.quarkiverse.langchain4j.sample.assistant.service;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import io.quarkiverse.langchain4j.sample.assistant.dto.McpConnection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class McpConnectionService {

    private final Map<String, McpConnectionData> connections = new ConcurrentHashMap<>();

    @Inject
    McpToolProvider mcpToolProvider;

    public McpConnection addConnection(io.quarkiverse.langchain4j.sample.assistant.service.McpConnectionRequest request) {
        if (connections.containsKey(request.name())) {
            throw new IllegalArgumentException("Connection with name '" + request.name() + "' already exists");
        }

        McpClient client = createMcpClient(request);
        connections.put(request.name(), new McpConnectionData(request, client));
        mcpToolProvider.addMcpClient(client);

        return new McpConnection(
            request.name(),
            request.transportType(),
            request.command(),
            request.url()
        );
    }

    public void removeConnection(String name) {
        McpConnectionData data = connections.remove(name);
        if (data != null) {
            mcpToolProvider.removeMcpClient(data.client());
            try {
                data.client().close();
            } catch (Exception e) {
                // Log error but continue
            }
        }
    }

    public List<McpConnection> listConnections() {
        List<McpConnection> result = new ArrayList<>();
        connections.forEach((name, data) -> {
            McpConnectionRequest req = data.request();
            result.add(new McpConnection(
                req.name(),
                req.transportType(),
                req.command(),
                req.url()
            ));
        });
        return result;
    }

    private McpClient createMcpClient(McpConnectionRequest request) {
        return switch (request.transportType()) {
            case STDIO -> {
                if (request.command() == null || request.command().isBlank()) {
                    throw new IllegalArgumentException("Command is required for STDIO transport");
                }
                List<String> commandParts = Arrays.asList(request.command().split("\\s+"));
                StdioMcpTransport transport = StdioMcpTransport.builder()
                    .command(commandParts)
                    .build();
                yield new DefaultMcpClient.Builder()
                    .key(request.name())
                    .transport(transport)
                    .build();
            }
            case STREAMABLE_HTTP -> {
                if (request.url() == null || request.url().isBlank()) {
                    throw new IllegalArgumentException("URL is required for STREAMABLE_HTTP transport");
                }
                StreamableHttpMcpTransport transport = StreamableHttpMcpTransport.builder()
                    .url(request.url())
                    .build();
                yield new DefaultMcpClient.Builder()
                    .key(request.name())
                    .transport(transport)
                    .build();
            }
        };
    }

    private record McpConnectionData(McpConnectionRequest request, McpClient client) {}
}
