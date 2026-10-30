package io.quarkiverse.langchain4j.mcp.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.mcp.client.McpClient;
import io.quarkiverse.langchain4j.mcp.auth.McpAuthenticationException;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import io.quarkiverse.langchain4j.mcp.test.mock.McpMockServer;
import io.quarkiverse.langchain4j.mcp.test.mock.McpTool;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;

/**
 * Verifies that when the server rejects a request with a non-2xx status and a body that is not
 * a JSON-RPC error, the failure carries the HTTP status code, so that callers can tell the cases apart
 * without parsing the message. Subclasses choose the protocol version spoken by the client and the server.
 * The client is configured not to cache the tool list, so that each test reaches the server.
 */
public abstract class AbstractMcpHttpErrorStatusTest extends WiremockAware {

    @Inject
    @McpClientName("client1")
    McpClient mcpClient;

    protected abstract McpMockServer<?> mcpServer();

    @BeforeEach
    void setUpMcpServer() {
        mcpServer()
                .reset()
                .stubInitialization()
                .stubTools(McpTool.ADD);
    }

    @Test
    public void rejectedCredentialCarriesTheStatusCode() {
        mcpServer().stubError("tools/list", 401);

        // in Quarkus, a 401 is reported as a dedicated exception that also carries the resource metadata
        Throwable failure = rootCauseOf(() -> mcpClient.listTools());
        assertThat(failure).isInstanceOf(McpAuthenticationException.class);
        assertThat(((McpAuthenticationException) failure).getStatusCode()).isEqualTo(401);
    }

    @Test
    public void serverErrorCarriesItsOwnStatusCode() {
        mcpServer().stubError("tools/list", 500);

        Throwable failure = rootCauseOf(() -> mcpClient.listTools());
        assertThat(failure).isInstanceOf(HttpException.class);
        assertThat(((HttpException) failure).statusCode()).isEqualTo(500);
    }

    @Test
    public void successfulRequestIsUnaffected() {
        assertThat(mcpClient.listTools())
                .extracting(ToolSpecification::name)
                .containsExactly("add");
    }

    private static Throwable rootCauseOf(Runnable action) {
        Throwable thrown = catchThrowable(action::run);
        assertThat(thrown).isNotNull();
        while (thrown.getCause() != null) {
            thrown = thrown.getCause();
        }
        return thrown;
    }
}
