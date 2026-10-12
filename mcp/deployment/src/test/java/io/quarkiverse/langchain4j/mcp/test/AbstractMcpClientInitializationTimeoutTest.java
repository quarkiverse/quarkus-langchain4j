package io.quarkiverse.langchain4j.mcp.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.mcp.client.McpClient;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import io.quarkiverse.langchain4j.mcp.test.mock.McpMockServer;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.arc.ClientProxy;

/**
 * Verify that the configured {@code initialization-timeout} is enforced by the MCP client
 * during the initialization handshake, and not just present in the configuration model.
 * <p>
 * The mock server deliberately delays the initialization response by {@link #SERVER_DELAY}:
 * a client configured with a shorter timeout must fail, while a client with a longer timeout
 * must initialize successfully.
 */
public abstract class AbstractMcpClientInitializationTimeoutTest extends WiremockAware {

    private static final Duration SERVER_DELAY = Duration.ofSeconds(2);

    protected abstract McpMockServer<?> mcpServer();

    @Inject
    @McpClientName("short-timeout")
    Instance<McpClient> shortTimeoutClient;

    @Inject
    @McpClientName("long-timeout")
    Instance<McpClient> longTimeoutClient;

    @BeforeEach
    void setUpMcpServer() {
        mcpServer().reset().stubSlowInitialization(SERVER_DELAY);
    }

    @Test
    public void initializationFailsWhenServerIsSlowerThanTheConfiguredTimeout() {
        assertThatThrownBy(() -> ClientProxy.unwrap(shortTimeoutClient.get()))
                .rootCause()
                .isInstanceOf(TimeoutException.class);
    }

    @Test
    public void initializationSucceedsWhenServerIsFasterThanTheConfiguredTimeout() {
        assertThat(ClientProxy.unwrap(longTimeoutClient.get())).isNotNull();
    }
}
