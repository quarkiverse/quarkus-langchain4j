package io.quarkiverse.langchain4j.mcp.test.mock;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;

/**
 * A mock MCP server speaking the legacy protocol (2025-11-25): the client opens a session using the
 * {@code initialize} handshake, and every subsequent message must carry the session ID assigned
 * by the server.
 */
public class LegacyMcpMockServer extends McpMockServer<LegacyMcpMockServer> {

    public static final String PROTOCOL_VERSION = "2025-11-25";
    public static final String SESSION_ID = "mock-session-id";

    LegacyMcpMockServer(WireMock wireMock, String path) {
        super(wireMock, path);
    }

    @Override
    protected MappingBuilder request(String method) {
        MappingBuilder builder = super.request(method);
        if (!method.equals("initialize")) {
            builder.withHeader("Mcp-Session-Id", equalTo(SESSION_ID));
        }
        return builder;
    }

    /**
     * Stubs the {@code initialize} request, which assigns the session ID, and accepts
     * the notifications sent by the client.
     */
    @Override
    public LegacyMcpMockServer stubInitialization() {
        ObjectNode result = MAPPER.createObjectNode();
        result.put("protocolVersion", PROTOCOL_VERSION);
        result.putObject("capabilities").putObject("tools");
        result.putObject("serverInfo")
                .put("name", "mock-legacy-server")
                .put("version", "1.0.0");
        register(request("initialize")
                .willReturn(response(result).withHeader("Mcp-Session-Id", SESSION_ID)));
        register(request("notifications/initialized").willReturn(aResponse().withStatus(202)));
        register(request("notifications/cancelled").willReturn(aResponse().withStatus(202)));
        return this;
    }

    @Override
    public LegacyMcpMockServer stubHealthCheck() {
        register(request("ping").willReturn(response(MAPPER.createObjectNode())));
        return this;
    }

    @Override
    public LegacyMcpMockServer failHealthCheck() {
        register(request("ping").atPriority(1).willReturn(aResponse().withStatus(500)));
        return this;
    }

    /**
     * Stubs a {@code tools/call} request for the given tool to stream a {@code notifications/message}
     * log notification on the request's SSE stream and then return a single text content.
     */
    public LegacyMcpMockServer stubToolCallWithLogMessage(String toolName, String level, String logger, String message,
            String resultText) {
        ObjectNode logParams = MAPPER.createObjectNode()
                .put("level", level)
                .put("logger", logger);
        logParams.putObject("data").put("message", message);

        ObjectNode resultMessage = MAPPER.createObjectNode();
        resultMessage.put("jsonrpc", "2.0");
        resultMessage.put("id", REQUEST_ID);
        resultMessage.set("result", textToolResult(resultText));

        register(toolCall(toolName)
                .willReturn(sseResponse(notification("notifications/message", logParams), resultMessage)));
        return this;
    }
}
