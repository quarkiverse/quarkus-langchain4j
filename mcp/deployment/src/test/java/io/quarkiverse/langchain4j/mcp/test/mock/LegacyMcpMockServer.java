package io.quarkiverse.langchain4j.mcp.test.mock;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;

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
        return stubError("ping", 500);
    }

    /**
     * Accepts the JSON-RPC responses that the client sends back for requests initiated by the server
     * (recognized by their {@code result}, which no client request has). The value pattern is needed,
     * because without it WireMock doesn't match an empty result like the one of a ping response.
     */
    public LegacyMcpMockServer stubClientResponses() {
        register(post()
                .withRequestBody(matchingJsonPath("$.result", matching(".*")))
                .withHeader("Mcp-Session-Id", equalTo(SESSION_ID))
                .willReturn(aResponse().withStatus(202)));
        return this;
    }

    /**
     * Stubs a {@code tools/call} request for the given tool to stream a server-initiated {@code ping}
     * request before the result. The ping deliberately reuses the JSON-RPC ID of the tool call: the client
     * and the server number their requests independently, so the IDs may collide, and the client must not
     * mistake its response to the ping for the still pending tool call.
     * Use together with {@link #stubClientResponses()}.
     */
    public LegacyMcpMockServer stubToolCallWithServerPing(String toolName, String resultText) {
        ObjectNode ping = MAPPER.createObjectNode();
        ping.put("jsonrpc", "2.0");
        ping.put("id", REQUEST_ID);
        ping.put("method", "ping");

        ObjectNode resultMessage = MAPPER.createObjectNode();
        resultMessage.put("jsonrpc", "2.0");
        resultMessage.put("id", REQUEST_ID);
        resultMessage.set("result", textToolResult(resultText));

        register(toolCall(toolName).willReturn(sseResponse(ping, resultMessage)));
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
