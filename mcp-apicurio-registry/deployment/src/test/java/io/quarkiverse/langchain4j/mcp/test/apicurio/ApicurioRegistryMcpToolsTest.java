package io.quarkiverse.langchain4j.mcp.test.apicurio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.Labels;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.quarkiverse.langchain4j.mcp.runtime.apicurio.ApicurioRegistryMcpTools;
import io.quarkus.test.QuarkusUnitTest;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ApicurioRegistryMcpToolsTest {

    // MCP_SERVER artifacts (registered content follows the official server.json schema) were
    // only added in 3.4.0; latest-snapshot is the image the a2a-apicurio-registry tests already
    // use for the same reason.
    private static final String REGISTRY_IMAGE = "quay.io/apicurio/apicurio-registry:latest-snapshot";
    private static final String REGISTRY_URL_PROPERTY = "test.apicurio.registry.url";
    private static final int MCP_SERVER_PORT = 8082;

    @SuppressWarnings("resource")
    static GenericContainer<?> registryContainer;
    static Process mcpServerProcess;

    static String registryUrl() {
        String existing = System.getProperty(REGISTRY_URL_PROPERTY);
        if (existing != null) {
            return existing;
        }
        registryContainer = new GenericContainer<>(REGISTRY_IMAGE)
                .withExposedPorts(8080)
                .waitingFor(Wait.forHttp("/apis/registry/v3/system/info")
                        .forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(2)));
        registryContainer.start();
        String url = "http://" + registryContainer.getHost() + ":"
                + registryContainer.getMappedPort(8080) + "/apis/registry/v3";
        System.setProperty(REGISTRY_URL_PROPERTY, url);
        return url;
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(McpServerHelper.class)
                    .addAsResource(
                            new StringAsset(
                                    "quarkus.http.test-port=0\n"
                                            + "quarkus.langchain4j.mcp.apicurio-registry.url=" + registryUrl()
                                            + "\n"),
                            "application.properties"));

    @Inject
    ApicurioRegistryMcpTools apicurioRegistryMcpTools;

    @BeforeAll
    static void setup() throws Exception {
        McpServerHelper.skipTestsIfJbangNotAvailable();
        mcpServerProcess = McpServerHelper.startServerHttp("simple_mcp_server.java", MCP_SERVER_PORT);
        registerArtifacts();
    }

    static void registerArtifacts() throws Exception {
        RegistryClientOptions options = RegistryClientOptions.create(registryUrl());
        RegistryClient client = RegistryClientFactory.create(options);

        String toolDefinition = """
                {
                    "name": "test-mcp-server",
                    "description": "A test MCP server for integration testing",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "query": {
                                "type": "string",
                                "description": "The search query"
                            }
                        }
                    }
                }
                """;

        Labels labels = new Labels();
        HashMap<String, Object> labelData = new HashMap<>();
        labelData.put("mcp-server-url", "http://localhost:" + MCP_SERVER_PORT + "/mcp");
        labelData.put("mcp-transport-type", "streamable-http");
        labels.setAdditionalData(labelData);

        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId("test-mcp-server");
        createArtifact.setArtifactType("MCP_TOOL");
        createArtifact.setName("Test MCP Server");
        createArtifact.setDescription("A test MCP server for integration testing");
        createArtifact.setLabels(labels);

        CreateVersion firstVersion = new CreateVersion();
        VersionContent content = new VersionContent();
        content.setContent(toolDefinition);
        content.setContentType("application/json");
        firstVersion.setContent(content);
        createArtifact.setFirstVersion(firstVersion);

        client.groups().byGroupId("test-group").artifacts().post(createArtifact);
    }

    @Test
    @Order(8)
    void searchFindsBothArtifactTypes() throws Exception {
        registerSearchDiscoveryFixtures();

        String result = apicurioRegistryMcpTools.searchMcpServers("zzdiscoverymarker");

        assertThat(result).contains("search-group/zz-discovery-tool").contains("[MCP_TOOL]");
        assertThat(result).contains("search-group/zz-discovery-server").contains("[MCP_SERVER]");
    }

    @Test
    @Order(9)
    void connectToMcpServerArtifactWithStreamableHttpRemote() throws Exception {
        registerMcpServerArtifact("mcp-server-group", "mcp-server-ok",
                serverJson("io.github.test/mcp-server-ok", "A server reachable over streamable-http", List.of(
                        remote("streamable-http", "http://localhost:" + MCP_SERVER_PORT + "/mcp"))));

        try {
            String result = apicurioRegistryMcpTools.connectMcpServer("mcp-server-ok", "mcp-server-group");
            assertThat(result).contains("Connected to MCP server");
        } finally {
            apicurioRegistryMcpTools.disconnectMcpServer("mcp-server-ok", "mcp-server-group");
        }
    }

    @Test
    @Order(10)
    void connectRejectsAnSseOnlyServerWithAClearMessage() throws Exception {
        registerMcpServerArtifact("mcp-server-group", "mcp-server-sse-only",
                serverJson("io.github.test/mcp-server-sse-only", "A server reachable only over sse", List.of(
                        remote("sse", "http://localhost:9999/sse"))));

        String result = apicurioRegistryMcpTools.connectMcpServer("mcp-server-sse-only", "mcp-server-group");

        assertThat(result).contains("Failed to connect").contains("sse").contains("streamable-http");
        assertThat(apicurioRegistryMcpTools.disconnectMcpServer("mcp-server-sse-only", "mcp-server-group"))
                .contains("No active connection");
    }

    @Test
    @Order(11)
    void connectRejectsAPackagesOnlyServerWithAClearMessage() throws Exception {
        String serverJson = """
                {
                  "name": "io.github.test/mcp-server-packages-only",
                  "description": "A server with only a local stdio package",
                  "version": "1.0.0",
                  "packages": [
                    { "registryType": "npm", "identifier": "@example/test-server", "version": "1.0.0",
                      "transport": { "type": "stdio" } }
                  ]
                }
                """;
        registerMcpServerArtifact("mcp-server-group", "mcp-server-packages-only", serverJson);

        String result = apicurioRegistryMcpTools.connectMcpServer("mcp-server-packages-only", "mcp-server-group");

        assertThat(result).contains("Failed to connect").contains("packages").contains("stdio");
        assertThat(apicurioRegistryMcpTools.disconnectMcpServer("mcp-server-packages-only", "mcp-server-group"))
                .contains("No active connection");
    }

    @Test
    @Order(12)
    void connectRejectsAServerWithNeitherRemotesNorPackages() throws Exception {
        String serverJson = """
                {
                  "name": "io.github.test/mcp-server-empty",
                  "description": "A server declaring nothing to connect to",
                  "version": "1.0.0"
                }
                """;
        registerMcpServerArtifact("mcp-server-group", "mcp-server-empty", serverJson);

        String result = apicurioRegistryMcpTools.connectMcpServer("mcp-server-empty", "mcp-server-group");

        assertThat(result).contains("Failed to connect").contains("no remotes or packages");
        assertThat(apicurioRegistryMcpTools.disconnectMcpServer("mcp-server-empty", "mcp-server-group"))
                .contains("No active connection");
    }

    @Test
    @Order(13)
    void connectPrefersStreamableHttpOverSseWhenBothAreDeclared() throws Exception {
        registerMcpServerArtifact("mcp-server-group", "mcp-server-multi-remote",
                serverJson("io.github.test/mcp-server-multi-remote",
                        "A server declaring both sse and streamable-http remotes", List.of(
                                remote("sse", "http://localhost:9999/sse"),
                                remote("streamable-http", "http://localhost:" + MCP_SERVER_PORT + "/mcp"))));

        try {
            String result = apicurioRegistryMcpTools.connectMcpServer("mcp-server-multi-remote", "mcp-server-group");
            assertThat(result).contains("Connected to MCP server");
        } finally {
            apicurioRegistryMcpTools.disconnectMcpServer("mcp-server-multi-remote", "mcp-server-group");
        }
    }

    @Test
    @Order(14)
    void connectRejectsARemoteWithAnUnresolvedVariablePlaceholder() throws Exception {
        String serverJson = """
                {
                  "name": "io.github.test/mcp-server-unresolved-variable",
                  "description": "A server whose remote url still has an unresolved {tenant} placeholder",
                  "version": "1.0.0",
                  "remotes": [
                    { "type": "streamable-http", "url": "http://localhost:%d/{tenant}/mcp",
                      "variables": { "tenant": { "isRequired": true } } }
                  ]
                }
                """.formatted(MCP_SERVER_PORT);
        registerMcpServerArtifact("mcp-server-group", "mcp-server-unresolved-variable", serverJson);

        String result = apicurioRegistryMcpTools.connectMcpServer("mcp-server-unresolved-variable",
                "mcp-server-group");

        assertThat(result).contains("Failed to connect").contains("unresolved variable placeholder");
        assertThat(apicurioRegistryMcpTools.disconnectMcpServer("mcp-server-unresolved-variable",
                "mcp-server-group")).contains("No active connection");
    }

    private static String remote(String type, String url) {
        return "{ \"type\": \"" + type + "\", \"url\": \"" + url + "\" }";
    }

    private static String serverJson(String name, String description, List<String> remotes) {
        return """
                {
                  "name": "%s",
                  "description": "%s",
                  "version": "1.0.0",
                  "remotes": [ %s ]
                }
                """.formatted(name, description, String.join(", ", remotes));
    }

    private static void registerMcpServerArtifact(String groupId, String artifactId, String serverJson)
            throws Exception {
        registerMcpServerArtifact(groupId, artifactId, serverJson, null);
    }

    // The registry's search "description" filter matches the artifact's metadata description, not
    // server.json's own description field, so a search fixture needs it set explicitly.
    private static void registerMcpServerArtifact(String groupId, String artifactId, String serverJson,
            String metadataDescription) throws Exception {
        RegistryClientOptions options = RegistryClientOptions.create(registryUrl());
        RegistryClient client = RegistryClientFactory.create(options);

        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType("MCP_SERVER");
        if (metadataDescription != null) {
            createArtifact.setDescription(metadataDescription);
        }

        CreateVersion firstVersion = new CreateVersion();
        VersionContent content = new VersionContent();
        content.setContent(serverJson);
        content.setContentType("application/json");
        firstVersion.setContent(content);
        createArtifact.setFirstVersion(firstVersion);

        client.groups().byGroupId(groupId).artifacts().post(createArtifact);
    }

    private static void registerSearchDiscoveryFixtures() throws Exception {
        RegistryClientOptions options = RegistryClientOptions.create(registryUrl());
        RegistryClient client = RegistryClientFactory.create(options);

        String toolDefinition = """
                {
                    "name": "zz-discovery-tool",
                    "description": "zzdiscoverymarker - a tool that should be found alongside the server",
                    "inputSchema": { "type": "object", "properties": { "q": { "type": "string" } } }
                }
                """;
        CreateArtifact tool = new CreateArtifact();
        tool.setArtifactId("zz-discovery-tool");
        tool.setArtifactType("MCP_TOOL");
        tool.setDescription("zzdiscoverymarker - a tool that should be found alongside the server");
        CreateVersion toolVersion = new CreateVersion();
        VersionContent toolContent = new VersionContent();
        toolContent.setContent(toolDefinition);
        toolContent.setContentType("application/json");
        toolVersion.setContent(toolContent);
        tool.setFirstVersion(toolVersion);
        client.groups().byGroupId("search-group").artifacts().post(tool);

        registerMcpServerArtifact("search-group", "zz-discovery-server",
                serverJson("io.github.test/zz-discovery-server", "zzdiscoverymarker - a server found alongside "
                        + "the tool", List.of(remote("streamable-http", "http://localhost:9999/mcp"))),
                "zzdiscoverymarker - a server found alongside the tool");
    }

    @AfterAll
    static void cleanup() {
        System.clearProperty(REGISTRY_URL_PROPERTY);
        if (registryContainer != null) {
            registryContainer.stop();
        }
        if (mcpServerProcess != null && mcpServerProcess.isAlive()) {
            McpServerHelper.destroyProcessTree(mcpServerProcess);
        }
    }

    @Test
    @Order(1)
    void beanIsInjected() {
        assertThat(apicurioRegistryMcpTools).isNotNull();
    }

    @Test
    @Order(2)
    void searchFindsRegisteredServer() {
        String result = apicurioRegistryMcpTools.searchMcpServers("integration testing");
        assertThat(result).contains("test-group/test-mcp-server");
    }

    @Test
    @Order(3)
    void searchReturnsNoResultsForUnknown() {
        String result = apicurioRegistryMcpTools.searchMcpServers("nonexistent-xyz-server");
        assertThat(result).contains("No MCP servers found");
    }

    @Test
    @Order(4)
    void connectToMcpServer() {
        String result = apicurioRegistryMcpTools.connectMcpServer("test-mcp-server", "test-group");
        assertThat(result).contains("Connected to MCP server");
    }

    @Test
    @Order(5)
    void connectIdempotent() {
        String result = apicurioRegistryMcpTools.connectMcpServer("test-mcp-server", "test-group");
        assertThat(result).contains("Already connected");
    }

    @Test
    @Order(6)
    void disconnectFromMcpServer() {
        String result = apicurioRegistryMcpTools.disconnectMcpServer("test-mcp-server", "test-group");
        assertThat(result).contains("Disconnected from MCP server");
    }

    @Test
    @Order(7)
    void disconnectNonexistent() {
        String result = apicurioRegistryMcpTools.disconnectMcpServer("nonexistent", "test-group");
        assertThat(result).contains("No active connection");
    }
}
