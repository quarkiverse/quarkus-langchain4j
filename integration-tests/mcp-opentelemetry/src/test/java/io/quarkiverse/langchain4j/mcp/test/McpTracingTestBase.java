package io.quarkiverse.langchain4j.mcp.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;

/**
 * Tests of the MCP client spans that are the same for all protocol versions and transports.
 */
public abstract class McpTracingTestBase {

    static final Duration TIMEOUT = Duration.ofSeconds(30);

    static final AttributeKey<String> MCP_METHOD_NAME = AttributeKey.stringKey("mcp.method.name");
    static final AttributeKey<String> MCP_PROTOCOL_VERSION = AttributeKey.stringKey("mcp.protocol.version");
    static final AttributeKey<String> MCP_SESSION_ID = AttributeKey.stringKey("mcp.session.id");
    static final AttributeKey<String> MCP_RESOURCE_URI = AttributeKey.stringKey("mcp.resource.uri");
    static final AttributeKey<String> JSONRPC_REQUEST_ID = AttributeKey.stringKey("jsonrpc.request.id");
    static final AttributeKey<String> GEN_AI_OPERATION_NAME = AttributeKey.stringKey("gen_ai.operation.name");
    static final AttributeKey<String> GEN_AI_TOOL_NAME = AttributeKey.stringKey("gen_ai.tool.name");
    static final AttributeKey<String> GEN_AI_PROMPT_NAME = AttributeKey.stringKey("gen_ai.prompt.name");
    static final AttributeKey<String> ERROR_TYPE = AttributeKey.stringKey("error.type");
    static final AttributeKey<String> RPC_RESPONSE_STATUS_CODE = AttributeKey.stringKey("rpc.response.status_code");
    static final AttributeKey<String> NETWORK_TRANSPORT = AttributeKey.stringKey("network.transport");
    static final AttributeKey<String> NETWORK_PROTOCOL_NAME = AttributeKey.stringKey("network.protocol.name");
    static final AttributeKey<String> SERVER_ADDRESS = AttributeKey.stringKey("server.address");
    static final AttributeKey<Long> SERVER_PORT = AttributeKey.longKey("server.port");

    @Inject
    @McpClientName("client1")
    McpClient mcpClient;

    @Inject
    TestSpanExporter spanExporter;

    @Inject
    Tracer tracer;

    /**
     * The MCP protocol version that the client is configured to use.
     */
    abstract String protocolVersion();

    /**
     * The port of the HTTP server, or {@code null} if the stdio transport is used.
     */
    abstract Integer httpPort();

    /**
     * The JSON-RPC error code that the server responds with when a resource doesn't exist.
     */
    abstract String resourceNotFoundErrorCode();

    /**
     * The method that the client uses to check the health of the server.
     */
    abstract String healthCheckMethod();

    /**
     * Verifies the {@code mcp.session.id} attribute, which depends on the protocol version and transport.
     */
    abstract void assertSessionId(SpanData span);

    @BeforeEach
    void clearSpans() {
        spanExporter.reset();
    }

    // ===== tools/call =====

    @Test
    public void successfulToolCall() {
        String result = mcpClient.executeTool(echoTraceparentRequest()).resultText();

        SpanData span = awaitSpan("tools/call echoMeta");

        assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(span.getAttributes().get(MCP_METHOD_NAME)).isEqualTo("tools/call");
        assertThat(span.getAttributes().get(JSONRPC_REQUEST_ID)).isNotNull();
        assertThat(span.getAttributes().get(GEN_AI_OPERATION_NAME)).isEqualTo("execute_tool");
        assertThat(span.getAttributes().get(GEN_AI_TOOL_NAME)).isEqualTo("echoMeta");
        assertThat(span.getAttributes().get(ERROR_TYPE)).isNull();
        assertThat(span.getAttributes().get(RPC_RESPONSE_STATUS_CODE)).isNull();
        assertThat(span.getStatus().getStatusCode()).isNotEqualTo(StatusCode.ERROR);
        assertCommonAttributes(span);

        // the trace context of the MCP span is propagated to the server
        assertThat(result).isEqualTo(traceparentOf(span));
        // the MCP span doesn't leak into the context of the caller
        assertThat(Span.current().getSpanContext().isValid()).isFalse();
    }

    @Test
    public void toolCallIsChildOfCurrentSpan() {
        Span parent = tracer.spanBuilder("parent").startSpan();
        String result;
        try (Scope ignored = parent.makeCurrent()) {
            result = mcpClient.executeTool(echoTraceparentRequest()).resultText();
            assertThat(Span.current()).isSameAs(parent);
        } finally {
            parent.end();
        }

        SpanData span = awaitSpan("tools/call echoMeta");

        assertThat(span.getTraceId()).isEqualTo(parent.getSpanContext().getTraceId());
        assertThat(span.getParentSpanId()).isEqualTo(parent.getSpanContext().getSpanId());
        assertThat(result).isEqualTo(traceparentOf(span));
    }

    @Test
    public void httpRequestSpanIsChildOfTheMcpSpan() {
        Assumptions.assumeTrue(httpPort() != null, "only the HTTP transport sends HTTP requests");

        mcpClient.executeTool(echoTraceparentRequest());

        SpanData mcpSpan = awaitSpan("tools/call echoMeta");
        // the span created by the instrumentation of the Vert.x HTTP client
        SpanData httpSpan = awaitSpan(spanExporter.getFinishedSpanItems(),
                s -> s.getKind() == SpanKind.CLIENT && s.getName().equals("POST")
                        && s.getParentSpanId().equals(mcpSpan.getSpanId()));
        assertThat(httpSpan.getTraceId()).isEqualTo(mcpSpan.getTraceId());
    }

    @Test
    public void asyncToolCall() {
        Span parent = tracer.spanBuilder("parent").startSpan();
        String result;
        try (Scope ignored = parent.makeCurrent()) {
            result = mcpClient.executeToolAsync(echoTraceparentRequest(), null).join().resultText();
            assertThat(Span.current()).isSameAs(parent);
        } finally {
            parent.end();
        }

        // the span is ended even though the call completes on a different thread
        SpanData span = awaitSpan("tools/call echoMeta");

        assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(span.getParentSpanId()).isEqualTo(parent.getSpanContext().getSpanId());
        assertThat(span.getAttributes().get(GEN_AI_TOOL_NAME)).isEqualTo("echoMeta");
        assertThat(span.getStatus().getStatusCode()).isNotEqualTo(StatusCode.ERROR);
        assertThat(result).isEqualTo(traceparentOf(span));
        assertThat(Span.current().getSpanContext().isValid()).isFalse();
    }

    @Test
    public void toolCallWithErrorResponse() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("errorResponse")
                .arguments("{}")
                .build();
        assertThatThrownBy(() -> mcpClient.executeTool(request));

        SpanData span = awaitSpan("tools/call errorResponse");

        assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(span.getAttributes().get(MCP_METHOD_NAME)).isEqualTo("tools/call");
        assertThat(span.getAttributes().get(GEN_AI_TOOL_NAME)).isEqualTo("errorResponse");
        assertThat(span.getAttributes().get(ERROR_TYPE)).isEqualTo("tool_error");
        assertThat(span.getAttributes().get(RPC_RESPONSE_STATUS_CODE)).isNull();
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    }

    @Test
    public void toolCallWithProtocolError() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("protocolError")
                .arguments("{}")
                .build();
        assertThatThrownBy(() -> mcpClient.executeTool(request));

        SpanData span = awaitSpan("tools/call protocolError");

        assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(span.getAttributes().get(GEN_AI_TOOL_NAME)).isEqualTo("protocolError");
        assertThat(span.getAttributes().get(RPC_RESPONSE_STATUS_CODE)).isEqualTo("-32603");
        assertThat(span.getAttributes().get(ERROR_TYPE)).isEqualTo("-32603");
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        // the description is the message of the JSON-RPC error
        assertThat(span.getStatus().getDescription()).isEqualTo("Internal failure");
    }

    @Test
    @Disabled("langchain4j-mcp doesn't notify listeners about -32602 errors of tools/call, it throws a ToolArgumentsException"
            + " that DefaultMcpClient.extractResultAndNotifyListeners doesn't catch")
    public void callNonExistentTool() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("nonExistentTool")
                .arguments("{}")
                .build();
        assertThatThrownBy(() -> mcpClient.executeTool(request));

        SpanData span = awaitSpan("tools/call nonExistentTool");

        assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(span.getAttributes().get(GEN_AI_TOOL_NAME)).isEqualTo("nonExistentTool");
        assertThat(span.getAttributes().get(RPC_RESPONSE_STATUS_CODE)).isEqualTo("-32602");
        assertThat(span.getAttributes().get(ERROR_TYPE)).isEqualTo("-32602");
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(span.getStatus().getDescription()).isNotBlank();
    }

    @Test
    public void toolCallTimeout() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("slowOperation")
                .arguments("{}")
                .build();
        try {
            mcpClient.executeTool(request);
        } catch (Exception ignored) {
            // timeout may or may not throw depending on the client implementation
        }

        // The span appearing in finished items proves span.end() was called (no leak)
        SpanData span = awaitSpan("tools/call slowOperation");

        assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(span.getAttributes().get(MCP_METHOD_NAME)).isEqualTo("tools/call");
        assertThat(span.getAttributes().get(GEN_AI_TOOL_NAME)).isEqualTo("slowOperation");
        assertThat(span.getAttributes().get(ERROR_TYPE)).isEqualTo("java.util.concurrent.TimeoutException");
        assertThat(span.getAttributes().get(RPC_RESPONSE_STATUS_CODE)).isNull();
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    }

    // ===== tools/list =====

    @Test
    public void listTools() {
        assertThat(mcpClient.listTools()).isNotEmpty();

        SpanData span = awaitSpan("tools/list");

        assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(span.getAttributes().get(MCP_METHOD_NAME)).isEqualTo("tools/list");
        assertThat(span.getAttributes().get(JSONRPC_REQUEST_ID)).isNotNull();
        assertThat(span.getAttributes().get(GEN_AI_OPERATION_NAME)).isNull();
        assertThat(span.getStatus().getStatusCode()).isNotEqualTo(StatusCode.ERROR);
        assertCommonAttributes(span);
    }

    @Test
    public void listSpanCoversEveryPage() {
        // forget the tools/list requests made before this test
        toolsListRequestsReceivedByServer();

        // the server returns the tools in pages, so the client sends more than one tools/list request
        mcpClient.listTools();

        SpanData span = awaitSpan("tools/list");
        List<String[]> requests = toolsListRequestsReceivedByServer();
        assertThat(requests).hasSizeGreaterThan(1);
        // one span for the whole operation, not one per page
        assertThat(spanExporter.getFinishedSpanItems()).filteredOn(s -> s.getName().equals("tools/list")).hasSize(1);
        // the ID of the first request that was actually sent, not of the request in the context of the listener
        assertThat(span.getAttributes().get(JSONRPC_REQUEST_ID)).isEqualTo(requests.get(0)[0]);
        // the trace context of the span is propagated with the request for every page
        assertThat(requests).allSatisfy(request -> assertThat(request[1]).isEqualTo(traceparentOf(span)));
    }

    // ===== resources =====

    @Test
    public void successfulResourceRead() {
        mcpClient.readResource("file:///greeting");

        SpanData span = awaitSpan("resources/read");

        assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(span.getAttributes().get(MCP_METHOD_NAME)).isEqualTo("resources/read");
        assertThat(span.getAttributes().get(JSONRPC_REQUEST_ID)).isNotNull();
        assertThat(span.getAttributes().get(MCP_RESOURCE_URI)).isEqualTo("file:///greeting");
        assertThat(span.getAttributes().get(ERROR_TYPE)).isNull();
        assertThat(span.getAttributes().get(RPC_RESPONSE_STATUS_CODE)).isNull();
        assertThat(span.getStatus().getStatusCode()).isNotEqualTo(StatusCode.ERROR);
        assertCommonAttributes(span);
    }

    @Test
    public void readNonExistentResource() {
        assertThatThrownBy(() -> mcpClient.readResource("file:///does-not-exist"));

        SpanData span = awaitSpan("resources/read");

        assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(span.getAttributes().get(MCP_METHOD_NAME)).isEqualTo("resources/read");
        assertThat(span.getAttributes().get(MCP_RESOURCE_URI)).isEqualTo("file:///does-not-exist");
        assertThat(span.getAttributes().get(ERROR_TYPE)).isEqualTo(resourceNotFoundErrorCode());
        assertThat(span.getAttributes().get(RPC_RESPONSE_STATUS_CODE)).isEqualTo(resourceNotFoundErrorCode());
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(span.getStatus().getDescription()).isNotBlank();
    }

    @Test
    public void listResources() {
        assertThat(mcpClient.listResources()).isNotEmpty();

        SpanData span = awaitSpan("resources/list");

        assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(span.getAttributes().get(MCP_METHOD_NAME)).isEqualTo("resources/list");
        assertThat(span.getAttributes().get(JSONRPC_REQUEST_ID)).isNotNull();
        assertThat(span.getStatus().getStatusCode()).isNotEqualTo(StatusCode.ERROR);
        assertCommonAttributes(span);
    }

    @Test
    public void listResourceTemplates() {
        assertThat(mcpClient.listResourceTemplates()).isNotEmpty();

        SpanData span = awaitSpan("resources/templates/list");

        assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(span.getAttributes().get(MCP_METHOD_NAME)).isEqualTo("resources/templates/list");
        assertThat(span.getAttributes().get(JSONRPC_REQUEST_ID)).isNotNull();
        assertThat(span.getStatus().getStatusCode()).isNotEqualTo(StatusCode.ERROR);
        assertCommonAttributes(span);
    }

    // ===== prompts =====

    @Test
    public void successfulPromptGet() {
        mcpClient.getPrompt("greeting_prompt", Collections.emptyMap());

        SpanData span = awaitSpan("prompts/get greeting_prompt");

        assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(span.getAttributes().get(MCP_METHOD_NAME)).isEqualTo("prompts/get");
        assertThat(span.getAttributes().get(JSONRPC_REQUEST_ID)).isNotNull();
        assertThat(span.getAttributes().get(GEN_AI_PROMPT_NAME)).isEqualTo("greeting_prompt");
        assertThat(span.getAttributes().get(ERROR_TYPE)).isNull();
        assertThat(span.getAttributes().get(RPC_RESPONSE_STATUS_CODE)).isNull();
        assertThat(span.getStatus().getStatusCode()).isNotEqualTo(StatusCode.ERROR);
        assertCommonAttributes(span);
    }

    @Test
    public void getNonExistentPrompt() {
        assertThatThrownBy(() -> mcpClient.getPrompt("nonExistentPrompt", Collections.emptyMap()));

        SpanData span = awaitSpan("prompts/get nonExistentPrompt");

        assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(span.getAttributes().get(MCP_METHOD_NAME)).isEqualTo("prompts/get");
        assertThat(span.getAttributes().get(GEN_AI_PROMPT_NAME)).isEqualTo("nonExistentPrompt");
        assertThat(span.getAttributes().get(ERROR_TYPE)).isEqualTo("-32602");
        assertThat(span.getAttributes().get(RPC_RESPONSE_STATUS_CODE)).isEqualTo("-32602");
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(span.getStatus().getDescription()).isNotBlank();
    }

    @Test
    public void listPrompts() {
        assertThat(mcpClient.listPrompts()).isNotEmpty();

        SpanData span = awaitSpan("prompts/list");

        assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(span.getAttributes().get(MCP_METHOD_NAME)).isEqualTo("prompts/list");
        assertThat(span.getAttributes().get(JSONRPC_REQUEST_ID)).isNotNull();
        assertThat(span.getStatus().getStatusCode()).isNotEqualTo(StatusCode.ERROR);
        assertCommonAttributes(span);
    }

    // ===== health check =====

    @Test
    public void healthCheckIsNotTraced() {
        mcpClient.checkHealth();
        // spans are exported in the order in which they end, so once the span of a later operation is exported,
        // the span of the health check would have been exported too
        mcpClient.listTools();
        awaitSpan("tools/list");

        assertThat(spanExporter.getFinishedSpanItems()).noneMatch(s -> s.getName().equals(healthCheckMethod()));
    }

    // ===== server-initiated notifications =====

    @Test
    public void toolListChangedNotification() {
        mcpClient.executeTool(ToolExecutionRequest.builder()
                .name("registerNewTool")
                .arguments("{}")
                .build());

        SpanData span = awaitSpan("notifications/tools/list_changed");

        assertThat(span.getKind()).isEqualTo(SpanKind.SERVER);
        assertThat(span.getAttributes().get(MCP_METHOD_NAME)).isEqualTo("notifications/tools/list_changed");
        assertThat(span.getAttributes().get(JSONRPC_REQUEST_ID)).isNull();
        assertCommonAttributes(span);
    }

    // ===== Helpers =====

    /**
     * The ID and the traceparent of every tools/list request that the server received since the last call.
     */
    List<String[]> toolsListRequestsReceivedByServer() {
        String result = mcpClient.executeTool(ToolExecutionRequest.builder()
                .name("toolsListRequests")
                .arguments("{}")
                .build()).resultText();
        return result.isEmpty() ? List.of() : result.lines().map(line -> line.split(" ")).toList();
    }

    static ToolExecutionRequest echoTraceparentRequest() {
        return ToolExecutionRequest.builder()
                .name("echoMeta")
                .arguments("{\"key\": \"traceparent\"}")
                .build();
    }

    static String traceparentOf(SpanData span) {
        return "00-" + span.getTraceId() + "-" + span.getSpanId() + "-" + span.getSpanContext().getTraceFlags().asHex();
    }

    void assertCommonAttributes(SpanData span) {
        assertThat(span.getAttributes().get(MCP_PROTOCOL_VERSION)).isEqualTo(protocolVersion());
        if (httpPort() != null) {
            assertThat(span.getAttributes().get(NETWORK_TRANSPORT)).isEqualTo("tcp");
            assertThat(span.getAttributes().get(NETWORK_PROTOCOL_NAME)).isEqualTo("http");
            assertThat(span.getAttributes().get(SERVER_ADDRESS)).isEqualTo("localhost");
            assertThat(span.getAttributes().get(SERVER_PORT)).isEqualTo(httpPort().longValue());
        } else {
            assertThat(span.getAttributes().get(NETWORK_TRANSPORT)).isEqualTo("pipe");
            assertThat(span.getAttributes().get(NETWORK_PROTOCOL_NAME)).isNull();
            assertThat(span.getAttributes().get(SERVER_ADDRESS)).isNull();
            assertThat(span.getAttributes().get(SERVER_PORT)).isNull();
        }
        assertSessionId(span);
    }

    SpanData awaitSpan(String spanName) {
        return awaitSpan(spanExporter.getFinishedSpanItems(), s -> s.getName().equals(spanName));
    }

    static SpanData awaitSpan(List<SpanData> spans, Predicate<SpanData> predicate) {
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(spans).anyMatch(predicate));
        return spans.stream().filter(predicate).findFirst().orElseThrow();
    }
}
