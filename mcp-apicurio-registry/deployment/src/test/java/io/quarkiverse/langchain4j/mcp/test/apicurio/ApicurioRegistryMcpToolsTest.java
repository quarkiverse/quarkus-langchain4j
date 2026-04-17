package io.quarkiverse.langchain4j.mcp.test.apicurio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
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
import io.apicurio.registry.rest.client.models.VersionContent;
import io.quarkiverse.langchain4j.mcp.runtime.apicurio.ApicurioRegistryMcpTools;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Integration test for ApicurioRegistryMcpTools that starts a real Apicurio Registry
 * instance using Testcontainers.
 * <p>
 * Requires a registry snapshot that includes MCP_TOOL artifact type support.
 * Re-enable once the latest-snapshot image includes this feature.
 */
@Disabled("Waiting for Apicurio Registry latest-snapshot to include MCP_TOOL artifact type support")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ApicurioRegistryMcpToolsTest {

    private static final String REGISTRY_IMAGE = "quay.io/apicurio/apicurio-registry:latest-snapshot";

    @SuppressWarnings("resource")
    static GenericContainer<?> registryContainer;

    static String registryUrl() {
        if (registryContainer == null) {
            registryContainer = new GenericContainer<>(REGISTRY_IMAGE)
                    .withExposedPorts(8080)
                    .waitingFor(Wait.forHttp("/apis/registry/v3/system/info")
                            .forStatusCode(200)
                            .withStartupTimeout(Duration.ofMinutes(2)));
            registryContainer.start();
        }
        return "http://" + registryContainer.getHost() + ":"
                + registryContainer.getMappedPort(8080) + "/apis/registry/v3";
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(MockStreamableHttpMcpServer.class)
                    .addAsResource(
                            new StringAsset(
                                    "quarkus.http.test-port=0\n"
                                            + "quarkus.langchain4j.mcp.apicurio-registry.url=" + registryUrl()
                                            + "\n"),
                            "application.properties"));

    @Inject
    ApicurioRegistryMcpTools apicurioRegistryMcpTools;

    @BeforeAll
    static void registerArtifacts() throws Exception {
        RegistryClientOptions options = RegistryClientOptions.create(registryUrl());
        RegistryClient client = RegistryClientFactory.create(options);

        // Register an MCP_TOOL artifact pointing to the mock server
        String serverDefinition = """
                {
                    "name": "test-mcp-server",
                    "description": "A test MCP server for integration testing",
                    "url": "http://localhost:8081/mock-streamable-mcp",
                    "transportType": "streamable-http"
                }
                """;

        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId("test-mcp-server");
        createArtifact.setArtifactType("MCP_TOOL");
        createArtifact.setName("Test MCP Server");
        createArtifact.setDescription("A test MCP server for integration testing");

        CreateVersion firstVersion = new CreateVersion();
        VersionContent content = new VersionContent();
        content.setContent(serverDefinition);
        content.setContentType("application/json");
        firstVersion.setContent(content);
        createArtifact.setFirstVersion(firstVersion);

        client.groups().byGroupId("test-group").artifacts().post(createArtifact);
    }

    @AfterAll
    static void stopContainer() {
        if (registryContainer != null) {
            registryContainer.stop();
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
        String result = apicurioRegistryMcpTools.searchMcpServers("test-mcp");
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
        String result = apicurioRegistryMcpTools.connectMcpServer("test-group", "test-mcp-server");
        assertThat(result).contains("Connected to MCP server");
    }

    @Test
    @Order(5)
    void connectIdempotent() {
        String result = apicurioRegistryMcpTools.connectMcpServer("test-group", "test-mcp-server");
        assertThat(result).contains("Already connected");
    }

    @Test
    @Order(6)
    void disconnectFromMcpServer() {
        String result = apicurioRegistryMcpTools.disconnectMcpServer("test-group", "test-mcp-server");
        assertThat(result).contains("Disconnected from MCP server");
    }

    @Test
    @Order(7)
    void disconnectNonexistent() {
        String result = apicurioRegistryMcpTools.disconnectMcpServer("test-group", "nonexistent");
        assertThat(result).contains("No active connection");
    }
}
