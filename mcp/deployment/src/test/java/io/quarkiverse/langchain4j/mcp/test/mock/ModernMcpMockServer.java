package io.quarkiverse.langchain4j.mcp.test.mock;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;

/**
 * A mock MCP server speaking the modern stateless protocol (2026-07-28). There is no handshake and no session;
 * instead, every request must carry the protocol version in its {@code _meta} and mirror it, along with
 * the method and (where applicable) the name, in HTTP headers. Requests that don't conform are not matched.
 *
 * @see <a href="https://modelcontextprotocol.io/specification/2026-07-28/basic/transports/streamable-http">Streamable
 *      HTTP transport</a>
 */
public class ModernMcpMockServer extends McpMockServer<ModernMcpMockServer> {

    public static final String PROTOCOL_VERSION = "2026-07-28";

    ModernMcpMockServer(WireMock wireMock, String path) {
        super(wireMock, path);
    }

    @Override
    protected MappingBuilder request(String method) {
        return super.request(method)
                .withHeader("MCP-Protocol-Version", equalTo(PROTOCOL_VERSION))
                .withHeader("Mcp-Method", equalTo(method))
                .withRequestBody(matchingJsonPath("$.params._meta['io.modelcontextprotocol/protocolVersion']",
                        equalTo(PROTOCOL_VERSION)));
    }

    @Override
    protected MappingBuilder toolCall(String toolName) {
        return super.toolCall(toolName)
                .withHeader("Mcp-Name", equalTo(toolName));
    }

    /**
     * Stubs {@code server/discover}, which the client uses to detect the protocol, and
     * {@code subscriptions/listen}, which the client sends right after it to subscribe to list changes.
     * The subscription stream only carries an acknowledgement in which the server agrees to none of the
     * requested notification types (it never sends any), and then ends.
     */
    @Override
    public ModernMcpMockServer stubInitialization() {
        register(request("server/discover").willReturn(response(discoverResult())));

        ObjectNode ackParams = MAPPER.createObjectNode();
        ackParams.putObject("_meta").put("io.modelcontextprotocol/subscriptionId", REQUEST_ID);
        ackParams.putObject("notifications");
        register(request("subscriptions/listen")
                .willReturn(sseResponse(notification("notifications/subscriptions/acknowledged", ackParams))));
        return this;
    }

    /**
     * The client checks the health of a modern server by sending {@code server/discover},
     * which {@link #stubInitialization()} already stubs.
     */
    @Override
    public ModernMcpMockServer stubHealthCheck() {
        return this;
    }

    @Override
    public ModernMcpMockServer failHealthCheck() {
        register(request("server/discover").atPriority(1).willReturn(aResponse().withStatus(500)));
        return this;
    }

    private static ObjectNode discoverResult() {
        ObjectNode result = MAPPER.createObjectNode();
        result.put("resultType", "complete");
        result.putArray("supportedVersions").add(PROTOCOL_VERSION);
        result.putObject("capabilities").putObject("tools");
        result.putObject("_meta")
                .putObject("io.modelcontextprotocol/serverInfo")
                .put("name", "mock-modern-server")
                .put("version", "1.0.0");
        return result;
    }
}
