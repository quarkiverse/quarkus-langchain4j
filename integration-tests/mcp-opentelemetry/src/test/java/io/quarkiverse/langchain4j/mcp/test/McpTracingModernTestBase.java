package io.quarkiverse.langchain4j.mcp.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.trace.data.SpanData;

/**
 * Tests of the MCP client spans specific to the stateless protocol (2026-07-28).
 */
public abstract class McpTracingModernTestBase extends McpTracingTestBase {

    @Override
    String protocolVersion() {
        return "2026-07-28";
    }

    @Override
    String resourceNotFoundErrorCode() {
        // changed from -32002 in the 2026-07-28 protocol
        return "-32602";
    }

    @Override
    String healthCheckMethod() {
        // the 2026-07-28 protocol has no ping
        return "server/discover";
    }

    @Override
    void assertSessionId(SpanData span) {
        // the 2026-07-28 protocol has no sessions
        assertThat(span.getAttributes().get(MCP_SESSION_ID)).isNull();
    }

    /**
     * Whether unsubscribing sends a {@code notifications/cancelled} notification to the server,
     * rather than just closing the stream of the subscription.
     */
    abstract boolean unsubscribeSendsCancellation();

    @Test
    public void serverDiscover() {
        SpanData span = awaitSpan(spanExporter.getAllSpans(), s -> s.getName().equals("server/discover"));

        assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(span.getAttributes().get(MCP_METHOD_NAME)).isEqualTo("server/discover");
        assertThat(span.getAttributes().get(JSONRPC_REQUEST_ID)).isNotNull();
        assertThat(span.getStatus().getStatusCode()).isNotEqualTo(StatusCode.ERROR);
        assertCommonAttributes(span);
        assertThat(spanExporter.getAllSpans()).noneMatch(s -> s.getName().equals("initialize"));
    }

    @Test
    public void resourceSubscription() {
        long subscriptionId = mcpClient.subscribeToResources(List.of("file:///status"));
        SpanData subscribeSpan = awaitSpan("subscriptions/listen");
        assertThat(subscribeSpan.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(subscribeSpan.getAttributes().get(MCP_METHOD_NAME)).isEqualTo("subscriptions/listen");
        assertThat(subscribeSpan.getAttributes().get(JSONRPC_REQUEST_ID)).isEqualTo(String.valueOf(subscriptionId));
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

        mcpClient.unsubscribeFromResources(subscriptionId);
        if (unsubscribeSendsCancellation()) {
            SpanData cancelSpan = awaitSpan("notifications/cancelled");
            assertThat(cancelSpan.getKind()).isEqualTo(SpanKind.CLIENT);
            assertThat(cancelSpan.getAttributes().get(MCP_METHOD_NAME)).isEqualTo("notifications/cancelled");
            assertThat(cancelSpan.getAttributes().get(JSONRPC_REQUEST_ID)).isNull();
            assertCommonAttributes(cancelSpan);
        } else {
            // no MCP message is sent, the client just closes the stream
            await().during(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(spanExporter.getFinishedSpanItems())
                    .noneMatch(s -> s.getName().equals("notifications/cancelled")));
        }
    }

    @Test
    public void cancellationIsChildOfTheCancelledRequest() {
        Assumptions.assumeTrue(unsubscribeSendsCancellation());

        long subscriptionId = mcpClient.subscribeToResources(List.of("file:///status"));
        SpanData subscribeSpan = awaitSpan("subscriptions/listen");

        mcpClient.unsubscribeFromResources(subscriptionId);
        SpanData cancelSpan = awaitSpan("notifications/cancelled");

        // "a cancellation belongs to the span of the request it cancels"
        assertThat(cancelSpan.getTraceId()).isEqualTo(subscribeSpan.getTraceId());
        assertThat(cancelSpan.getParentSpanId()).isEqualTo(subscribeSpan.getSpanId());
    }
}
