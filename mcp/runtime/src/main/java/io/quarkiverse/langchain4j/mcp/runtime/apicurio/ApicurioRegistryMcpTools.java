package io.quarkiverse.langchain4j.mcp.runtime.apicurio;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.SearchedArtifact;
import io.quarkiverse.langchain4j.mcp.runtime.http.QuarkusStreamableHttpMcpTransport;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;

/**
 * Provides tools for discovering and connecting to MCP servers registered in Apicurio Registry.
 * <p>
 * This class is NOT a CDI bean by default. It is conditionally registered as a synthetic bean
 * by the deployment processor when the Apicurio Registry configuration is present and the SDK
 * is on the classpath. It can also be created programmatically via {@link ApicurioRegistryMcpToolsBuilder}.
 */
public class ApicurioRegistryMcpTools {

    private final RegistryClient registryClient;
    private final McpToolProvider mcpToolProvider;
    private final Vertx vertx;
    private final Map<String, McpClient> connectedServers = new ConcurrentHashMap<>();

    public ApicurioRegistryMcpTools(RegistryClient registryClient, McpToolProvider mcpToolProvider, Vertx vertx) {
        this.registryClient = registryClient;
        this.mcpToolProvider = mcpToolProvider;
        this.vertx = vertx;
    }

    @Tool("Search Apicurio Registry for available MCP servers matching a query. "
            + "Returns server names, descriptions, and versions.")
    public String searchMcpServers(String query) {
        var results = registryClient.search().artifacts().get(config -> {
            config.queryParameters.name = query;
            config.queryParameters.artifactType = "MCP_TOOL";
            config.queryParameters.limit = 20;
        });

        if (results == null || results.getArtifacts() == null || results.getArtifacts().isEmpty()) {
            return "No MCP servers found matching '" + query + "'";
        }

        return results.getArtifacts().stream()
                .map(this::formatArtifact)
                .collect(Collectors.joining("\n\n"));
    }

    @Tool("Connect to an MCP server discovered from the registry. "
            + "Provide the groupId and artifactId of the server to connect to. "
            + "After connecting, the server's tools become available for use.")
    public String connectMcpServer(String groupId, String artifactId) {
        String key = groupId + "/" + artifactId;

        if (connectedServers.containsKey(key)) {
            return "Already connected to MCP server '" + key + "'";
        }

        try {
            String content = new String(
                    registryClient.groups().byGroupId(groupId)
                            .artifacts().byArtifactId(artifactId)
                            .versions().byVersionExpression("branch=latest")
                            .content().get().readAllBytes(),
                    StandardCharsets.UTF_8);

            McpServerDefinition serverDef = McpServerDefinition.fromJson(content);

            McpClient client = createMcpClient(key, serverDef);
            connectedServers.put(key, client);
            mcpToolProvider.addMcpClient(client);

            return "Connected to MCP server '" + key + "'. Its tools are now available.";
        } catch (IOException e) {
            return "Failed to connect to MCP server '" + key + "': " + e.getMessage();
        }
    }

    @Tool("Disconnect a previously connected MCP server. "
            + "Provide the groupId and artifactId of the server to disconnect.")
    public String disconnectMcpServer(String groupId, String artifactId) {
        String key = groupId + "/" + artifactId;
        McpClient client = connectedServers.remove(key);

        if (client == null) {
            return "No active connection to MCP server '" + key + "'";
        }

        mcpToolProvider.removeMcpClient(client);
        try {
            client.close();
        } catch (Exception e) {
            // best effort
        }
        return "Disconnected from MCP server '" + key + "'";
    }

    private McpClient createMcpClient(String key, McpServerDefinition serverDef) {
        QuarkusStreamableHttpMcpTransport transport = new QuarkusStreamableHttpMcpTransport.Builder()
                .url(serverDef.url())
                .httpClient(vertx.createHttpClient(new HttpClientOptions()))
                .mcpClientName(key)
                .build();

        return new DefaultMcpClient.Builder()
                .key(key)
                .transport(transport)
                .build();
    }

    private String formatArtifact(SearchedArtifact artifact) {
        StringBuilder sb = new StringBuilder();
        sb.append("- **").append(artifact.getGroupId()).append("/").append(artifact.getArtifactId()).append("**");
        if (artifact.getName() != null) {
            sb.append(" (").append(artifact.getName()).append(")");
        }
        if (artifact.getDescription() != null) {
            sb.append("\n  ").append(artifact.getDescription());
        }
        return sb.toString();
    }
}
