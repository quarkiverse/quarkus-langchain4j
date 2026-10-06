package io.quarkiverse.langchain4j.mcp.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.inject.Inject;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.mcp.runtime.McpClientHealthCheck;
import io.quarkiverse.langchain4j.mcp.test.mock.McpMockServer;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;

/**
 * Verifies that the readiness check reports the MCP client as down once the server stops
 * answering health checks. Subclasses choose the protocol version spoken by the client and the server.
 */
public abstract class AbstractMcpHealthCheckTest extends WiremockAware {

    @Inject
    @Readiness
    McpClientHealthCheck healthCheck;

    protected abstract McpMockServer<?> mcpServer();

    @BeforeEach
    void setUpMcpServer() {
        mcpServer()
                .reset()
                .stubInitialization()
                .stubHealthCheck();
    }

    @Test
    public void test() {
        HealthCheckResponse response = healthCheck.call();
        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());

        mcpServer().failHealthCheck();

        response = healthCheck.call();
        assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus());
    }
}
