package io.quarkiverse.langchain4j.mcp.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import io.quarkiverse.langchain4j.jaxrsclient.JaxRsHttpClientBuilder;
import io.quarkiverse.langchain4j.mcp.auth.McpAuthenticationException;
import io.quarkiverse.langchain4j.mcp.auth.McpClientAuthProvider;
import io.quarkiverse.langchain4j.mcp.runtime.http.McpHttpClients;
import io.quarkiverse.langchain4j.mcp.test.mock.McpMockServer;
import io.quarkiverse.langchain4j.mcp.test.mock.McpTool;
import io.quarkiverse.langchain4j.mcp.test.mock.ModernMcpMockServer;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verifies the HTTP clients that {@link McpHttpClients} creates for MCP clients that the application builds itself,
 * for example for MCP servers that only become known at runtime. The mock server accepts only the token of the
 * provider passed explicitly, not the one of the {@link McpClientAuthProvider} bean.
 */
public class McpHttpClientsTest extends WiremockAware {

    private static final String MCP_PATH = "/mcp/http-clients";

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(GlobalMcpAuthProvider.class)
                    .addPackage(McpMockServer.class.getPackage())
                    .addAsResource(new StringAsset("quarkus.langchain4j.openai.api-key=whatever"),
                            "application.properties"));

    @BeforeEach
    void setUpMcpServer() {
        McpMockServer.modern(wiremock(), MCP_PATH)
                .reset()
                .requireAuthorization("Bearer explicit-token")
                .stubInitialization()
                .stubTools(McpTool.ADD);
    }

    @Test
    void explicitAuthProviderTakesPrecedenceOverTheBean() throws Exception {
        McpClientAuthProvider explicit = input -> "Bearer explicit-token";

        try (McpClient client = mcpClient(McpHttpClients.builder(explicit))) {
            assertThat(client.listTools())
                    .extracting(ToolSpecification::name)
                    .containsExactly("add");
        }
    }

    @Test
    void namedClientFallsBackToTheAuthProviderBean() {
        // the bean's token is not accepted, so the server's rejection shows that the bean was used
        Throwable failure = catchThrowable(() -> {
            try (McpClient client = mcpClient(McpHttpClients.builder("dynamic-client"))) {
                client.listTools();
            }
        });

        assertThat(failure).isNotNull();
        Throwable rootCause = failure;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        assertThat(rootCause).isInstanceOf(McpAuthenticationException.class);
        assertThat(((McpAuthenticationException) rootCause).getStatusCode()).isEqualTo(401);
    }

    private McpClient mcpClient(JaxRsHttpClientBuilder httpClientBuilder) {
        StreamableHttpMcpTransport transport = StreamableHttpMcpTransport.builder()
                .url(resolvedWiremockUrl(MCP_PATH))
                .httpClientBuilder(httpClientBuilder)
                .build();
        return new DefaultMcpClient.Builder()
                .key("dynamic-client")
                .transport(transport)
                .protocolVersion(ModernMcpMockServer.PROTOCOL_VERSION)
                .cacheToolList(false)
                .build();
    }

    @ApplicationScoped
    public static class GlobalMcpAuthProvider implements McpClientAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            return "Bearer global-token";
        }
    }
}
