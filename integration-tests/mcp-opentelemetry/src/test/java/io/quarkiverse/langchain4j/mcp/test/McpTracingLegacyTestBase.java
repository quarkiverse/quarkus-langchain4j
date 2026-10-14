package io.quarkiverse.langchain4j.mcp.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpRoot;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.trace.data.SpanData;

/**
 * Tests of the MCP client spans specific to the legacy stateful protocol (2025-11-25).
 */
public abstract class McpTracingLegacyTestBase extends McpTracingTestBase {

    @Override
    String protocolVersion() {
        return "2025-11-25";
    }

    @Override
    String resourceNotFoundErrorCode() {
        return "-32002";
    }

    @Override
    String healthCheckMethod() {
        return "ping";
    }

    @Override
    void assertSessionId(SpanData span) {
        if (httpPort() != null) {
            assertThat(span.getAttributes().get(MCP_SESSION_ID)).isNotBlank();
        } else {
            // the stdio transport has no sessions
            assertThat(span.getAttributes().get(MCP_SESSION_ID)).isNull();
        }
    }

    @Test
    public void initialize() {
        SpanData span = awaitSpan(spanExporter.getAllSpans(), s -> s.getName().equals("initialize"));

        assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(span.getAttributes().get(MCP_METHOD_NAME)).isEqualTo("initialize");
        assertThat(span.getAttributes().get(JSONRPC_REQUEST_ID)).isNotNull();
        assertThat(span.getStatus().getStatusCode()).isNotEqualTo(StatusCode.ERROR);
        assertCommonAttributes(span);
        assertThat(spanExporter.getAllSpans()).noneMatch(s -> s.getName().equals("server/discover"));
    }

    @Test
    public void allSpansShareTheSession() {
        mcpClient.listTools();
        mcpClient.executeTool(echoTraceparentRequest());

        String sessionId = awaitSpan("tools/list").getAttributes().get(MCP_SESSION_ID);
        assertThat(awaitSpan("tools/call echoMeta").getAttributes().get(MCP_SESSION_ID)).isEqualTo(sessionId);
    }

    @Test
    public void resourceSubscription() {
        mcpClient.subscribeToResource("file:///status");
        SpanData subscribeSpan = awaitSpan("resources/subscribe");
        assertThat(subscribeSpan.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(subscribeSpan.getAttributes().get(MCP_METHOD_NAME)).isEqualTo("resources/subscribe");
        assertThat(subscribeSpan.getAttributes().get(JSONRPC_REQUEST_ID)).isNotNull();
        assertThat(subscribeSpan.getAttributes().get(MCP_RESOURCE_URI)).isEqualTo("file:///status");
        assertThat(subscribeSpan.getStatus().getStatusCode()).isNotEqualTo(StatusCode.ERROR);
        assertCommonAttributes(subscribeSpan);

        mcpClient.executeTool(ToolExecutionRequest.builder()
                .name("updateStatus")
                .arguments("{\"newValue\": \"updated\"}")
                .build());
        SpanData updatedSpan = awaitSpan("notifications/resources/updated");
        assertThat(updatedSpan.getKind()).isEqualTo(SpanKind.SERVER);
        assertThat(updatedSpan.getAttributes().get(MCP_METHOD_NAME)).isEqualTo("notifications/resources/updated");
        assertThat(updatedSpan.getAttributes().get(MCP_RESOURCE_URI)).isEqualTo("file:///status");
        assertCommonAttributes(updatedSpan);

        mcpClient.unsubscribeFromResource("file:///status");
        SpanData unsubscribeSpan = awaitSpan("resources/unsubscribe");
        assertThat(unsubscribeSpan.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(unsubscribeSpan.getAttributes().get(MCP_METHOD_NAME)).isEqualTo("resources/unsubscribe");
        assertThat(unsubscribeSpan.getAttributes().get(JSONRPC_REQUEST_ID)).isNotNull();
        assertThat(unsubscribeSpan.getAttributes().get(MCP_RESOURCE_URI)).isEqualTo("file:///status");
        assertThat(unsubscribeSpan.getStatus().getStatusCode()).isNotEqualTo(StatusCode.ERROR);
        assertCommonAttributes(unsubscribeSpan);
    }

    @Test
    public void rootsListChanged() {
        mcpClient.setRoots(List.of(new McpRoot("workspace", "file:///workspace")));

        // the client notifies the server
        SpanData notificationSpan = awaitSpan("notifications/roots/list_changed");
        assertThat(notificationSpan.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(notificationSpan.getAttributes().get(MCP_METHOD_NAME)).isEqualTo("notifications/roots/list_changed");
        assertThat(notificationSpan.getAttributes().get(JSONRPC_REQUEST_ID)).isNull();
        assertCommonAttributes(notificationSpan);

        // which makes the server ask the client for its roots
        SpanData rootsListSpan = awaitSpan("roots/list");
        assertThat(rootsListSpan.getKind()).isEqualTo(SpanKind.SERVER);
        assertThat(rootsListSpan.getAttributes().get(MCP_METHOD_NAME)).isEqualTo("roots/list");
        assertCommonAttributes(rootsListSpan);
    }

    @Test
    public void logMessageNotification() {
        mcpClient.executeTool(ToolExecutionRequest.builder()
                .name("logMessage")
                .arguments("{}")
                .build());

        SpanData span = awaitSpan("notifications/message");

        assertThat(span.getKind()).isEqualTo(SpanKind.SERVER);
        assertThat(span.getAttributes().get(MCP_METHOD_NAME)).isEqualTo("notifications/message");
        assertCommonAttributes(span);
    }

    // with the legacy protocol, the client always notifies the server when a request times out
    @Test
    @Disabled("langchain4j-mcp sends the notifications/cancelled of a timed out request without its McpCallContext,"
            + " and only after notifying the listeners about the timeout")
    public void timeoutCancellationIsChildOfTheCancelledToolCall() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("slowOperation")
                .arguments("{}")
                .build();
        try {
            mcpClient.executeTool(request);
        } catch (Exception ignored) {
            // the tool execution timeout is expected to trip
        }

        SpanData toolCallSpan = awaitSpan("tools/call slowOperation");
        SpanData cancelSpan = awaitSpan("notifications/cancelled");

        assertThat(cancelSpan.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(cancelSpan.getTraceId()).isEqualTo(toolCallSpan.getTraceId());
        assertThat(cancelSpan.getParentSpanId()).isEqualTo(toolCallSpan.getSpanId());
    }
}
