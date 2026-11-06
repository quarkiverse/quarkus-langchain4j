package io.quarkiverse.langchain4j.mcp.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpCallContext;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpHeadersSupplier;
import dev.langchain4j.mcp.protocol.McpCallToolParams;
import dev.langchain4j.mcp.protocol.McpCallToolRequest;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import io.quarkiverse.langchain4j.mcp.test.mock.McpMockServer;
import io.quarkiverse.langchain4j.mcp.test.mock.McpTool;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;

/**
 * Headers configured through {@code quarkus.langchain4j.mcp.<name>.header.*} and a
 * {@link McpHeadersSupplier} CDI bean are two independent ways of adding HTTP headers to the requests an
 * MCP client sends, and an application may well use both: a fixed credential in configuration plus a
 * per-call header computed from the {@link McpCallContext}. Both must end up on the wire, and where the
 * two name the same header, the dynamically supplied value wins.
 * <p>
 * Subclasses choose the protocol version spoken by the client and the server.
 */
public abstract class AbstractMcpCombinedHeadersTest extends WiremockAware {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * A header only configuration contributes.
     */
    public static final String STATIC_HEADER = "X-Static-Header";

    public static final String STATIC_HEADER_VALUE = "static-value";

    /**
     * A header configuration contributes a fallback value for, which the supplier overrides whenever it
     * has something more specific to say.
     */
    public static final String OVERRIDDEN_HEADER = "X-Tool-Name";

    public static final String OVERRIDDEN_HEADER_FALLBACK = "no-tool";

    @Inject
    @McpClientName("client1")
    McpClient client;

    protected abstract McpMockServer<?> mcpServer();

    @BeforeEach
    void setUpMcpServer() {
        mcpServer()
                .reset()
                .stubInitialization()
                .stubTools(McpTool.ADD)
                .stubToolCall("add", "{\"a\": 5, \"b\": 12}", "The sum of 5 and 12 is 17.");
        resetRequests();
    }

    /**
     * On a tool call the supplier has a value for {@value #OVERRIDDEN_HEADER}, so that one is the tool name
     * rather than the configured fallback - but the configured {@value #STATIC_HEADER}, which the supplier
     * says nothing about, must be sent too.
     */
    @Test
    public void aToolCallCarriesBothTheConfiguredAndTheSuppliedHeaders() {
        String result = client.executeTool(ToolExecutionRequest.builder()
                .name("add")
                .arguments("{\"a\": 5, \"b\": 12}")
                .build()).resultText();
        assertThat(result).isEqualTo("The sum of 5 and 12 is 17.");

        assertThat(requestsWithMethod("tools/call"))
                .isNotEmpty()
                .allSatisfy(request -> {
                    assertThat(request.getHeader(STATIC_HEADER)).isEqualTo(STATIC_HEADER_VALUE);
                    assertThat(request.getHeader(OVERRIDDEN_HEADER)).isEqualTo("add");
                });
    }

    /**
     * The supplier contributes nothing to a {@code tools/list} request, so it must carry the configured
     * headers unchanged - including the configured fallback for {@value #OVERRIDDEN_HEADER}.
     */
    @Test
    public void aRequestTheSupplierContributesNothingToCarriesTheConfiguredHeaders() {
        assertThat(client.listTools()).extracting(spec -> spec.name()).containsExactly("add");

        assertThat(requestsWithMethod("tools/list"))
                .isNotEmpty()
                .allSatisfy(request -> {
                    assertThat(request.getHeader(STATIC_HEADER)).isEqualTo(STATIC_HEADER_VALUE);
                    assertThat(request.getHeader(OVERRIDDEN_HEADER)).isEqualTo(OVERRIDDEN_HEADER_FALLBACK);
                });
    }

    private List<LoggedRequest> requestsWithMethod(String method) {
        return wiremock().getServeEvents().stream()
                .map(ServeEvent::getRequest)
                .filter(request -> method.equals(jsonRpcMethod(request)))
                .toList();
    }

    private static String jsonRpcMethod(LoggedRequest request) {
        byte[] body = request.getBody();
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            JsonNode message = MAPPER.readTree(body);
            return message.path("method").asText(null);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Supplies the name of the tool being called as the {@value #OVERRIDDEN_HEADER} header, and nothing at
     * all for any other kind of message.
     */
    @ApplicationScoped
    public static class ToolNameHeadersSupplier implements McpHeadersSupplier {

        @Override
        public Map<String, String> apply(McpCallContext context) {
            if (context != null && context.message() instanceof McpCallToolRequest toolRequest) {
                return Map.of(OVERRIDDEN_HEADER, ((McpCallToolParams) toolRequest.getParams()).getName());
            }
            return Map.of();
        }
    }
}
