package io.quarkiverse.langchain4j.mcp.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolProvider;
import io.quarkiverse.langchain4j.mcp.runtime.http.QuarkusStreamableHttpMcpTransport;
import io.quarkiverse.langchain4j.mcp.runtime.http.StreamableHttpMcpTransportListener;
import io.quarkiverse.langchain4j.mcp.test.mock.McpMockServer;
import io.quarkiverse.langchain4j.mcp.test.mock.McpTool;
import io.quarkiverse.langchain4j.mcp.test.mock.ModernMcpMockServer;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusExtensionTest;
import io.vertx.core.Vertx;

/**
 * Verifies that a {@link StreamableHttpMcpTransportListener} bean is notified about every Streamable HTTP transport
 * before the transport is built, both for a client declared in configuration and for a transport built
 * programmatically, and that what it changes takes effect: both clients are given a URL nothing answers on, which
 * the listener replaces with the one of the mock server.
 */
public class McpTransportListenerTest extends WiremockAware {

    private static final String MCP_PATH = "/mcp/transport-listener";
    private static final String WRONG_PATH = "/mcp/nothing-answers-here";

    @RegisterExtension
    static QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RedirectingListener.class)
                    .addPackage(McpMockServer.class.getPackage())
                    .addAsResource(new StringAsset("""
                            quarkus.langchain4j.openai.api-key=whatever
                            quarkus.langchain4j.mcp.client1.transport-type=streamable-http
                            quarkus.langchain4j.mcp.client1.protocol-version=%s
                            quarkus.langchain4j.mcp.client1.url=%s
                            """.formatted(ModernMcpMockServer.PROTOCOL_VERSION, wiremockUrlForConfig(WRONG_PATH))),
                            "application.properties"));

    @Inject
    ToolProvider toolProvider;

    @Inject
    Vertx vertx;

    @BeforeEach
    void setUpMcpServer() {
        McpMockServer.modern(wiremock(), MCP_PATH)
                .reset()
                .stubInitialization()
                .stubTools(McpTool.ADD);
    }

    @Test
    public void listenerIsNotifiedAboutConfiguredClient() {
        assertThat(toolProvider.provideTools(null).tools().keySet())
                .extracting(ToolSpecification::name)
                .containsExactly("add");
        assertThat(RedirectingListener.CLIENT_NAMES).containsOnlyOnce("client1");
    }

    @Test
    public void listenerIsNotifiedAboutProgrammaticallyBuiltTransport() throws Exception {
        QuarkusStreamableHttpMcpTransport transport = new QuarkusStreamableHttpMcpTransport.Builder()
                .mcpClientName("built")
                .url(resolvedWiremockUrl(WRONG_PATH))
                .httpClient(vertx.createHttpClient())
                .build();
        try (McpClient client = new DefaultMcpClient.Builder()
                .clientName("built")
                .protocolVersion(ModernMcpMockServer.PROTOCOL_VERSION)
                .autoHealthCheck(false)
                .transport(transport)
                .build()) {
            assertThat(client.listTools())
                    .extracting(ToolSpecification::name)
                    .containsExactly("add");
        }
        assertThat(RedirectingListener.CLIENT_NAMES).containsOnlyOnce("built");
    }

    @ApplicationScoped
    public static class RedirectingListener implements StreamableHttpMcpTransportListener {

        static final List<String> CLIENT_NAMES = new CopyOnWriteArrayList<>();

        @Override
        public void onNewTransport(QuarkusStreamableHttpMcpTransport.Builder builder) {
            CLIENT_NAMES.add(builder.getMcpClientName());
            builder.url(builder.getUrl().replace(WRONG_PATH, MCP_PATH));
        }
    }
}
