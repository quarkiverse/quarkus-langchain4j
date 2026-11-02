package io.quarkiverse.langchain4j.mcp.test.apicurio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;

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

    private static final String REGISTRY_IMAGE = "quay.io/apicurio/apicurio-registry:3.3.0";
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
