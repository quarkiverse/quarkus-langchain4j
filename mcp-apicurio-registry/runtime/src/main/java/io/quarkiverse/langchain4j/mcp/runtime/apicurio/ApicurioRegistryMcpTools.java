package io.quarkiverse.langchain4j.mcp.runtime.apicurio;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
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
    private static final String MCP_TOOL_TYPE = "MCP_TOOL";
    private static final String MCP_SERVER_TYPE = "MCP_SERVER";
    private static final String STREAMABLE_HTTP = "streamable-http";

    // Matches an unsubstituted {variable} placeholder left in a remote's url, as declared by
    // server.json's `variables` map. A registry-discovered server has no per-user substitution
    // step, so a remaining placeholder means the URL is not actually usable as-is.
    private static final Pattern UNRESOLVED_VARIABLE = Pattern.compile("\\{[^{}]+\\}");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RegistryClient registryClient;
    private final McpToolProvider mcpToolProvider;
    private final Vertx vertx;
    private final Map<String, McpClient> connectedServers = new ConcurrentHashMap<>();

    public ApicurioRegistryMcpTools(RegistryClient registryClient, McpToolProvider mcpToolProvider, Vertx vertx) {
        this.registryClient = registryClient;
        this.mcpToolProvider = mcpToolProvider;
        this.vertx = vertx;
    }

    @Tool("Search Apicurio Registry for available MCP servers and tools. "
            + "The query matches against name, description, and artifactId. "
            + "Returns groupId, artifactId, name, description and artifact type for each match.")
    public String searchMcpServers(@P("Search query to find MCP servers") String query) {
        String formatted = Stream.concat(
                search(query, MCP_SERVER_TYPE).stream(),
                search(query, MCP_TOOL_TYPE).stream())
                .map(this::formatArtifact)
                .collect(Collectors.joining("\n\n"));

        return formatted.isEmpty() ? "No MCP servers found matching '" + query + "'" : formatted;
    }

    private List<SearchedArtifact> search(String query, String artifactType) {
        var results = registryClient.search().artifacts().get(config -> {
            config.queryParameters.description = query;
            config.queryParameters.artifactType = artifactType;
            config.queryParameters.limit = 20;
        });
        return results == null || results.getArtifacts() == null ? List.of() : results.getArtifacts();
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

            String url;
            if (MCP_SERVER_TYPE.equals(metadata.getArtifactType())) {
                RemoteResolution resolution = resolveRemoteUrl(resolvedGroup, artifactId);
                if (resolution.error() != null) {
                    return "Failed to connect to MCP server '" + key + "': " + resolution.error();
                }
                url = resolution.url();
            } else {
                // MCP_TOOL (or any other type): preserve the existing label-based connection path.
                Labels labels = metadata.getLabels();
                Map<String, Object> labelData = labels != null ? labels.getAdditionalData() : null;
                if (labelData == null || labelData.isEmpty()) {
                    return "Failed to connect to MCP server '" + key
                            + "': artifact has no labels with connection metadata";
                }
                url = labelData.get("mcp-server-url") != null ? labelData.get("mcp-server-url").toString() : null;
                if (url == null || url.isBlank()) {
                    return "Failed to connect to MCP server '" + key + "': missing 'mcp-server-url' label";
                }
            }

            McpServerDefinition serverDef = new McpServerDefinition(url, STREAMABLE_HTTP);

            McpClient client = createMcpClient(key, serverDef);
            connectedServers.put(key, client);
            mcpToolProvider.addMcpClient(client);

            // List tools so the LLM knows what's available and can call them via callMcpTool
            StringBuilder toolList = new StringBuilder();
            toolList.append("Connected to MCP server '").append(key).append("'. Available tools:\n");
            try {
                for (var spec : client.listTools()) {
                    toolList.append("- ").append(spec.name());
                    if (spec.description() != null) {
                        toolList.append(": ").append(spec.description());
                    }
                    toolList.append("\n");
                }
                toolList.append("\nUse callMcpTool to invoke any of these tools.");
            } catch (Exception e) {
                toolList.append("(could not list tools: ").append(e.getMessage()).append(")");
            }
            return toolList.toString();
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
            return "Failed to connect to MCP server '" + key + "': " + msg;
        }
    }

    /**
     * Picks a usable remote URL from an {@code MCP_SERVER} artifact's {@code server.json}, or
     * explains why none is usable. Only {@code streamable-http} is supported for
     * registry-discovered servers, so an {@code sse}-only or {@code packages}-only (stdio)
     * declaration is reported clearly instead of silently defaulting to streamable-http.
     */
    private RemoteResolution resolveRemoteUrl(String groupId, String artifactId) {
        JsonNode server;
        try (InputStream content = registryClient.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).versions().byVersionExpression("branch=latest").content().get()) {
            server = OBJECT_MAPPER.readTree(content);
        } catch (Exception e) {
            return RemoteResolution.error("could not read or parse the server's registered content ("
                    + e.getMessage() + ")");
        }

        JsonNode remotes = server.get("remotes");
        if (remotes != null && remotes.isArray() && !remotes.isEmpty()) {
            String sseUrl = null;
            for (JsonNode remote : (Iterable<JsonNode>) remotes::elements) {
                JsonNode type = remote.get("type");
                JsonNode url = remote.get("url");
                if (type == null || url == null || url.asText().isBlank()) {
                    continue;
                }
                if (STREAMABLE_HTTP.equals(type.asText())) {
                    String remoteUrl = url.asText();
                    if (UNRESOLVED_VARIABLE.matcher(remoteUrl).find()) {
                        return RemoteResolution.error("its streamable-http remote url '" + remoteUrl
                                + "' still contains an unresolved variable placeholder");
                    }
                    return RemoteResolution.ok(remoteUrl);
                }
                if ("sse".equals(type.asText()) && sseUrl == null) {
                    sseUrl = url.asText();
                }
            }
            if (sseUrl != null) {
                return RemoteResolution.error("it only declares an sse remote (" + sseUrl
                        + "); only streamable-http is supported for registry-discovered servers");
            }
        }

        JsonNode packages = server.get("packages");
        if (packages != null && packages.isArray() && !packages.isEmpty()) {
            return RemoteResolution.error("it only declares local packages (stdio); only streamable-http "
                    + "remote transport is supported for registry-discovered servers");
        }

        return RemoteResolution.error("it declares no remotes or packages to connect to");
    }

    private record RemoteResolution(String url, String error) {
        static RemoteResolution ok(String url) {
            return new RemoteResolution(url, null);
        }

        static RemoteResolution error(String error) {
            return new RemoteResolution(null, error);
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

    @Tool("Call a tool on a connected MCP server. Use this after connecting to a server "
            + "to invoke its tools. The toolName and arguments must match the server's tool definition.")
    public String callMcpTool(
            @P("The key of the connected server (format: groupId/artifactId)") String serverKey,
            @P("The name of the tool to call") String toolName,
            @P("The arguments as a JSON string, e.g. {\"city\":\"Amsterdam\"}") String arguments) {
        McpClient client = connectedServers.get(serverKey);
        if (client == null) {
            return "No connected MCP server with key '" + serverKey + "'. Connect first using connectMcpServer.";
        }

        try {
            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .name(toolName)
                    .arguments(arguments)
                    .build();
            var result = client.executeTool(request);
            return result.resultText();
        } catch (Exception e) {
            return "Error calling tool '" + toolName + "' on '" + serverKey + "': " + e.getMessage();
        }
    }

    // Only streamable-http transport is supported for dynamically discovered servers.
    // STDIO is not applicable since registry-discovered servers are network-accessible.
    // Server authentication is not yet supported — a future enhancement could read
    // auth hints from artifact labels (MCP_TOOL) or server.json headers/variables (MCP_SERVER)
    // and wire them into the transport.
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
        sb.append(" [").append(artifact.getArtifactType()).append("]");
        if (artifact.getDescription() != null) {
            sb.append("\n  ").append(artifact.getDescription());
        }
        return sb.toString();
    }
}
