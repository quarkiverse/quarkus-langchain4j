package io.quarkiverse.langchain4j.mcp.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import io.quarkiverse.langchain4j.mcp.auth.McpClientAuthProvider;
import io.quarkiverse.langchain4j.mcp.runtime.QuarkusMcpToolProvider;
import io.quarkiverse.langchain4j.mcp.test.mock.McpMockServer;
import io.quarkiverse.langchain4j.mcp.test.mock.McpTool;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.arc.ClientProxy;

/**
 * Verifies that the access token provided by a {@link McpClientAuthProvider} is sent with every request.
 * The mock server answers requests without the expected {@code Authorization} header with a 401.
 * Subclasses choose the protocol version spoken by the client and the server.
 */
public abstract class AbstractMcpAccessTokenTest extends WiremockAware {

    @Inject
    ToolProvider toolProvider;

    protected abstract McpMockServer<?> mcpServer();

    @BeforeEach
    void setUpMcpServer() {
        mcpServer()
                .reset()
                .requireAuthorization("Bearer test-token")
                .stubInitialization()
                .stubTools(McpTool.ADD);
    }

    @Test
    public void toolProviderShouldBeMcpBased() {
        assertThat(ClientProxy.unwrap(toolProvider)).isInstanceOf(QuarkusMcpToolProvider.class);
    }

    @Test
    public void providingTools() {
        ToolProviderResult toolProviderResult = toolProvider.provideTools(null);

        assertThat(toolProviderResult.tools().keySet())
                .extracting(ToolSpecification::name)
                .containsExactly("add");
    }

    @ApplicationScoped
    public static class TestMcpAuthProvider implements McpClientAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            try {
                Thread.sleep(5000);
                return "Bearer test-token";
            } catch (Exception ex) {
            }
            return null;
        }

    }
}
