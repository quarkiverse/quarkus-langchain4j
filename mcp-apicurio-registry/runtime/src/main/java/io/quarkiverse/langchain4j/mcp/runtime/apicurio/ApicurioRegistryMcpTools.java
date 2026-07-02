package io.quarkiverse.langchain4j.mcp.runtime.apicurio;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.models.Labels;
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

    private static final String DEFAULT_GROUP = "default";

    private final RegistryClient registryClient;
    private final McpToolProvider mcpToolProvider;
    private final Vertx vertx;
    private final Map<String, McpClient> connectedServers = new ConcurrentHashMap<>();

    public ApicurioRegistryMcpTools(RegistryClient registryClient, McpToolProvider mcpToolProvider, Vertx vertx) {
        this.registryClient = registryClient;
        this.mcpToolProvider = mcpToolProvider;
        this.vertx = vertx;
    }

    @Tool("Search Apicurio Registry for available MCP servers. "
            + "The query matches against server name, description, and artifactId. "
            + "Returns groupId, artifactId, name, and description for each match.")
    public String searchMcpServers(@P("Search query to find MCP servers") String query) {
        var results = registryClient.search().artifacts().get(config -> {
            config.queryParameters.description = query;
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

    @Tool("Connect to an MCP server discovered from the registry by its artifactId. "
            + "Use the artifactId from the search results. "
            + "The groupId defaults to 'default' if not specified or null. "
            + "After connecting, the server's tools become available for use.")
    public String connectMcpServer(
            @P("The artifactId of the MCP server to connect to") String artifactId,
            @P("The groupId of the MCP server, defaults to 'default' if null") String groupId) {
        String resolvedGroup = resolveGroupId(groupId);
        String key = resolvedGroup + "/" + artifactId;

        if (connectedServers.containsKey(key)) {
            return "Already connected to MCP server '" + key + "'";
        }

        try {
            ArtifactMetaData metadata = registryClient.groups().byGroupId(resolvedGroup)
                    .artifacts().byArtifactId(artifactId).get();

            Labels labels = metadata.getLabels();
            Map<String, Object> labelData = labels != null ? labels.getAdditionalData() : null;
            if (labelData == null || labelData.isEmpty()) {
                return "Failed to connect to MCP server '" + key
                        + "': artifact has no labels with connection metadata";
            }

            String url = labelData.get("mcp-server-url") != null
                    ? labelData.get("mcp-server-url").toString()
                    : null;
            String transportType = labelData.get("mcp-transport-type") != null
                    ? labelData.get("mcp-transport-type").toString()
                    : null;
            if (url == null || url.isBlank()) {
                return "Failed to connect to MCP server '" + key
                        + "': missing 'mcp-server-url' label";
            }

            McpServerDefinition serverDef = new McpServerDefinition(url,
                    transportType != null ? transportType : "streamable-http");

            McpClient client = createMcpClient(key, serverDef);
            connectedServers.put(key, client);
            mcpToolProvider.addMcpClient(client);

            return "Connected to MCP server '" + key + "'. Its tools are now available.";
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
            return "Failed to connect to MCP server '" + key + "': " + msg;
        }
    }

    @Tool("Disconnect a previously connected MCP server. "
            + "The groupId defaults to 'default' if not specified or null.")
    public String disconnectMcpServer(
            @P("The artifactId of the MCP server to disconnect") String artifactId,
            @P("The groupId of the MCP server, defaults to 'default' if null") String groupId) {
        String resolvedGroup = resolveGroupId(groupId);
        String key = resolvedGroup + "/" + artifactId;
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

    // Only streamable-http transport is supported for dynamically discovered servers.
    // STDIO is not applicable since registry-discovered servers are network-accessible.
    // Server authentication is not yet supported — a future enhancement could read
    // auth hints from artifact labels and wire them into the transport.
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

    private static String resolveGroupId(String groupId) {
        if (groupId == null || groupId.isBlank() || "null".equals(groupId)) {
            return DEFAULT_GROUP;
        }
        return groupId;
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
