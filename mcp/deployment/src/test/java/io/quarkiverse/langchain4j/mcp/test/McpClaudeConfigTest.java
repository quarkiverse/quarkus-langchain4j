package io.quarkiverse.langchain4j.mcp.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.util.List;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.mcp.client.McpClient;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import io.quarkiverse.langchain4j.mcp.runtime.config.McpClientRuntimeConfig;
import io.quarkiverse.langchain4j.mcp.runtime.config.McpRuntimeConfiguration;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.config.SmallRyeConfig;

public class McpClaudeConfigTest {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(AbstractMockHttpMcpServer.class, MockHttpMcpServer.class)
                    .addAsResource(new StringAsset("""
                            {
                              "mcpServers": {
                                "gemini": {
                                  "command": "npx",
                                  "args": ["-y", "github:aliargun/mcp-server-gemini"],
                                  "env": {
                                    "GEMINI_API_KEY": "your_api_key_here",
                                    "DEBUG": "true"
                                  }
                                },
                                "file.system": {
                                  "command": "npx",
                                  "args": [
                                    "-y",
                                    "@modelcontextprotocol/server-filesystem",
                                    "/Users/username/Desktop",
                                    "/Users/username/Downloads"
                                  ]
                                },
                                "MCP_DOCKER": {
                                  "command": "docker",
                                  "args": [
                                    "run",
                                    "-i",
                                    "--rm",
                                    "alpine/socat",
                                    "STDIO",
                                    "TCP:host.docker.internal:8811"
                                  ]
                                }
                              }
                            }
                            """),
                            "mcp-config.json"))
            .overrideConfigKey("quarkus.langchain4j.mcp.config-file", "mcp-config.json")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.transport-type", "http")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.url", "http://localhost:8081/mock-mcp/sse")
            .overrideConfigKey("quarkus.langchain4j.mcp.\"file.system\".log-requests", "true")
            .overrideConfigKey("quarkus.langchain4j.mcp.MCP_DOCKER.log-responses", "true")
            .overrideConfigKey("quarkus.langchain4j.mcp.MCP_DOCKER.command", "podman,run,-i,--rm,alpine/socat")
            .overrideRuntimeConfigKey("quarkus.log.category.\"dev.langchain4j\".level", "DEBUG")
            .overrideRuntimeConfigKey("quarkus.log.category.\"dev.langchain4j\".level", "DEBUG");

    @Inject
    @McpClientName("gemini")
    Instance<McpClient> geminiInstance;

    @Inject
    @McpClientName("file.system")
    Instance<McpClient> filesystemInstance;

    @Inject
    @McpClientName("MCP_DOCKER")
    Instance<McpClient> dockerInstance;

    @Inject
    @McpClientName("client1")
    Instance<McpClient> client1Instance;

    @Inject
    @McpClientName("dummy")
    Instance<McpClient> dummyInstance;

    @Test
    public void testBeans() {
        assertThat(geminiInstance.isResolvable()).isTrue();
        assertThat(filesystemInstance.isResolvable()).isTrue();
        assertThat(dockerInstance.isResolvable()).isTrue();

        assertThat(client1Instance.isResolvable()).isTrue();

        assertThat(dummyInstance.isResolvable()).isFalse();
    }

    @Test
    public void testConfig() {
        SmallRyeConfig smallRyeConfig = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class);
        McpRuntimeConfiguration mcpRuntimeConfiguration = smallRyeConfig.getConfigMapping(McpRuntimeConfiguration.class);
        assertThat(mcpRuntimeConfiguration).isNotNull();
        assertThat(mcpRuntimeConfiguration.clients()).containsOnlyKeys("client1", "file.system", "MCP_DOCKER", "gemini");

        McpClientRuntimeConfig filesystemConfig = mcpRuntimeConfiguration.clients().get("file.system");
        assertThat(filesystemConfig.command()).contains(List.of("npx", "-y",
                "@modelcontextprotocol/server-filesystem", "/Users/username/Desktop", "/Users/username/Downloads"));
        assertThat(filesystemConfig.environment()).isEmpty();
        assertThat(filesystemConfig.logRequests()).contains(true);
        assertThat(filesystemConfig.logResponses()).isEmpty();

        McpClientRuntimeConfig geminiConfig = mcpRuntimeConfiguration.clients().get("gemini");
        assertThat(geminiConfig.command()).contains(List.of("npx", "-y", "github:aliargun/mcp-server-gemini"));
        assertThat(geminiConfig.environment())
                .containsOnly(entry("GEMINI_API_KEY", "your_api_key_here"), entry("DEBUG", "true"));
        assertThat(geminiConfig.logRequests()).isEmpty();
        assertThat(geminiConfig.logResponses()).isEmpty();

        McpClientRuntimeConfig dockerConfig = mcpRuntimeConfiguration.clients().get("MCP_DOCKER");
        assertThat(dockerConfig.command()).contains(List.of("podman", "run", "-i", "--rm", "alpine/socat"));
        assertThat(dockerConfig.environment()).isEmpty();
        assertThat(dockerConfig.logRequests()).isEmpty();
        assertThat(dockerConfig.logResponses()).contains(true);
    }
}
