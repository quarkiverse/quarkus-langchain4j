package io.quarkiverse.langchain4j.mcp.runtime.http;

import dev.langchain4j.mcp.protocol.McpClientMessage;

/**
 * Sends the HTTP request that carries an MCP message, so that integrations can choose the context the request is
 * sent in. For example, tracing sends it in the context of the span of the message, which makes the span of the
 * HTTP request its child.
 */
@FunctionalInterface
public interface McpRequestSender {

    McpRequestSender DIRECT = (message, send) -> send.run();

    /**
     * @param message the MCP message carried by the request
     * @param send sends the request
     */
    void send(McpClientMessage message, Runnable send);
}
