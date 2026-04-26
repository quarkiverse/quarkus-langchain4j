package io.quarkiverse.langchain4j.mcp.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Collections;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;

public abstract class McpTracingTestBase {

    static final Duration TIMEOUT = Duration.ofSeconds(30);

    static final AttributeKey<String> MCP_METHOD_NAME = AttributeKey.stringKey("mcp.method.name");
    static final AttributeKey<String> JSONRPC_REQUEST_ID = AttributeKey.stringKey("jsonrpc.request.id");
    static final AttributeKey<String> GEN_AI_OPERATION_NAME = AttributeKey.stringKey("gen_ai.operation.name");
    static final AttributeKey<String> GEN_AI_TOOL_NAME = AttributeKey.stringKey("gen_ai.tool.name");
    static final AttributeKey<String> GEN_AI_PROMPT_NAME = AttributeKey.stringKey("gen_ai.prompt.name");
    static final AttributeKey<String> MCP_RESOURCE_URI = AttributeKey.stringKey("mcp.resource.uri");
    static final AttributeKey<String> ERROR_TYPE = AttributeKey.stringKey("error.type");
    static final AttributeKey<String> RPC_RESPONSE_STATUS_CODE = AttributeKey.stringKey("rpc.response.status_code");

    @Inject
    @McpClientName("client1")
    McpClient mcpClient;

    @Inject
    InMemorySpanExporter spanExporter;

    @BeforeEach
    void clearSpans() {
        spanExporter.reset();
    }

    // ===== Tool call tests =====

    @Test
    public void successfulToolCall() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("echoMeta")
                .arguments("{\"key\": \"traceparent\"}")
                .build();
        String result = mcpClient.executeTool(request).resultText();

        SpanData span = awaitSpan("tools/call echoMeta");

        assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(span.getAttributes().get(MCP_METHOD_NAME)).isEqualTo("tools/call");
        assertThat(span.getAttributes().get(JSONRPC_REQUEST_ID)).isNotNull();
        assertThat(span.getAttributes().get(GEN_AI_OPERATION_NAME)).isEqualTo("execute_tool");
        assertThat(span.getAttributes().get(GEN_AI_TOOL_NAME)).isEqualTo("echoMeta");
        assertThat(span.getAttributes().get(ERROR_TYPE)).isNull();
        assertThat(span.getAttributes().get(RPC_RESPONSE_STATUS_CODE)).isNull();
        assertThat(span.getStatus().getStatusCode()).isNotEqualTo(StatusCode.ERROR);

        // Verify trace context was propagated to the MCP server
        assertThat(result).isNotEqualTo("null");
        assertThat(result).startsWith("00-");
        assertThat(result).contains(span.getTraceId());
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
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
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

    // ===== Resource read tests =====

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
    }

    @Test
    public void readNonExistentResource() {
        assertThatThrownBy(() -> mcpClient.readResource("file:///does-not-exist"));

        SpanData span = awaitSpan("resources/read");

        assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(span.getAttributes().get(MCP_METHOD_NAME)).isEqualTo("resources/read");
        assertThat(span.getAttributes().get(MCP_RESOURCE_URI)).isEqualTo("file:///does-not-exist");
        assertThat(span.getAttributes().get(ERROR_TYPE)).isNotNull();
        assertThat(span.getAttributes().get(RPC_RESPONSE_STATUS_CODE)).isEqualTo("-32002");
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    }

    // ===== Prompt get tests =====

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
    }

    @Test
    public void getNonExistentPrompt() {
        assertThatThrownBy(() -> mcpClient.getPrompt("nonExistentPrompt", Collections.emptyMap()));

        SpanData span = awaitSpan("prompts/get nonExistentPrompt");

        assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(span.getAttributes().get(MCP_METHOD_NAME)).isEqualTo("prompts/get");
        assertThat(span.getAttributes().get(GEN_AI_PROMPT_NAME)).isEqualTo("nonExistentPrompt");
        assertThat(span.getAttributes().get(ERROR_TYPE)).isNotNull();
        assertThat(span.getAttributes().get(RPC_RESPONSE_STATUS_CODE)).isEqualTo("-32602");
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    }

    // ===== Helpers =====

    private SpanData awaitSpan(String spanName) {
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(spanExporter.getFinishedSpanItems())
                .anyMatch(s -> s.getName().equals(spanName)));
        return spanExporter.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals(spanName))
                .findFirst()
                .orElseThrow();
    }

    @ApplicationScoped
    public static class InMemorySpanExporterProducer {
        @Produces
        @ApplicationScoped
        InMemorySpanExporter exporter() {
            return InMemorySpanExporter.create();
        }
    }
}
