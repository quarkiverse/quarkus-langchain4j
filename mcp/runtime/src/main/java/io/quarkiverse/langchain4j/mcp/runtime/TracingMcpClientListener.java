package io.quarkiverse.langchain4j.mcp.runtime;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

import org.jboss.logging.Logger;

import dev.langchain4j.mcp.client.McpCallContext;
import dev.langchain4j.mcp.client.McpClientListener;
import dev.langchain4j.mcp.client.McpException;
import dev.langchain4j.mcp.client.McpGetPromptResult;
import dev.langchain4j.mcp.client.McpReadResourceResult;
import dev.langchain4j.mcp.protocol.McpCallToolParams;
import dev.langchain4j.mcp.protocol.McpCallToolRequest;
import dev.langchain4j.mcp.protocol.McpGetPromptParams;
import dev.langchain4j.mcp.protocol.McpGetPromptRequest;
import dev.langchain4j.mcp.protocol.McpReadResourceParams;
import dev.langchain4j.mcp.protocol.McpReadResourceRequest;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

/**
 * An MCP client listener that creates OpenTelemetry spans for MCP client operations.
 * <p>
 * Follows the OpenTelemetry MCP client semantic conventions.
 *
 * @see <a href="https://opentelemetry.io/docs/specs/semconv/gen-ai/mcp/#client">MCP Client Semantic Conventions</a>
 */
public class TracingMcpClientListener implements McpClientListener {

    // MCP semantic convention attribute keys
    private static final AttributeKey<String> MCP_METHOD_NAME = AttributeKey.stringKey("mcp.method.name");
    private static final AttributeKey<String> JSONRPC_REQUEST_ID = AttributeKey.stringKey("jsonrpc.request.id");
    private static final AttributeKey<String> GEN_AI_OPERATION_NAME = AttributeKey.stringKey("gen_ai.operation.name");
    private static final AttributeKey<String> GEN_AI_TOOL_NAME = AttributeKey.stringKey("gen_ai.tool.name");
    private static final AttributeKey<String> GEN_AI_PROMPT_NAME = AttributeKey.stringKey("gen_ai.prompt.name");
    private static final AttributeKey<String> MCP_RESOURCE_URI = AttributeKey.stringKey("mcp.resource.uri");
    private static final AttributeKey<String> ERROR_TYPE = AttributeKey.stringKey("error.type");
    private static final AttributeKey<String> RPC_RESPONSE_STATUS_CODE = AttributeKey.stringKey("rpc.response.status_code");

    private final Tracer tracer;
    private final Map<McpCallContext, SpanAndScope> activeSpans = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Logger log = Logger.getLogger(TracingMcpClientListener.class);

    private record SpanAndScope(Span span, Scope scope) {
    }

    public TracingMcpClientListener(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public void beforeExecuteTool(McpCallContext context) {
        McpCallToolRequest request = (McpCallToolRequest) context.message();
        McpCallToolParams params = (McpCallToolParams) request.getParams();
        String toolName = params.getName();
        Span span = tracer.spanBuilder("tools/call " + toolName)
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute(MCP_METHOD_NAME, "tools/call")
                .setAttribute(JSONRPC_REQUEST_ID, String.valueOf(request.getId()))
                .setAttribute(GEN_AI_OPERATION_NAME, "execute_tool")
                .setAttribute(GEN_AI_TOOL_NAME, toolName)
                .startSpan();
        Scope scope = span.makeCurrent();
        activeSpans.put(context, new SpanAndScope(span, scope));
    }

    @Override
    public void afterExecuteTool(McpCallContext context, ToolExecutionResult result, Map<String, Object> rawResult) {
        endSpan(context, null, result != null && result.isError() ? "tool_error" : null);
    }

    @Override
    public void onExecuteToolError(McpCallContext context, Throwable error) {
        endSpan(context, error);
    }

    @Override
    public void beforeResourceGet(McpCallContext context) {
        McpReadResourceRequest request = (McpReadResourceRequest) context.message();
        McpReadResourceParams params = (McpReadResourceParams) request.getParams();
        String uri = params.getUri();
        Span span = tracer.spanBuilder("resources/read")
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute(MCP_METHOD_NAME, "resources/read")
                .setAttribute(JSONRPC_REQUEST_ID, String.valueOf(request.getId()))
                .setAttribute(MCP_RESOURCE_URI, uri)
                .startSpan();
        Scope scope = span.makeCurrent();
        activeSpans.put(context, new SpanAndScope(span, scope));
    }

    @Override
    public void afterResourceGet(McpCallContext context, McpReadResourceResult result, Map<String, Object> rawResult) {
        endSpan(context, null);
    }

    @Override
    public void onResourceGetError(McpCallContext context, Throwable error) {
        endSpan(context, error);
    }

    @Override
    public void beforePromptGet(McpCallContext context) {
        McpGetPromptRequest request = (McpGetPromptRequest) context.message();
        McpGetPromptParams params = (McpGetPromptParams) request.getParams();
        String promptName = params.getName();
        Span span = tracer.spanBuilder("prompts/get " + promptName)
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute(MCP_METHOD_NAME, "prompts/get")
                .setAttribute(JSONRPC_REQUEST_ID, String.valueOf(request.getId()))
                .setAttribute(GEN_AI_PROMPT_NAME, promptName)
                .startSpan();
        Scope scope = span.makeCurrent();
        activeSpans.put(context, new SpanAndScope(span, scope));
    }

    @Override
    public void afterPromptGet(McpCallContext context, McpGetPromptResult result, Map<String, Object> rawResult) {
        endSpan(context, null);
    }

    @Override
    public void onPromptGetError(McpCallContext context, Throwable error) {
        endSpan(context, error);
    }

    private void endSpan(McpCallContext context, Throwable error) {
        endSpan(context, error, null);
    }

    private void endSpan(McpCallContext context, Throwable error, String errorType) {
        SpanAndScope sas = activeSpans.remove(context);
        if (sas == null) {
            log.warn("Unknown call context: " + context.message().getId());
            return;
        }
        try (Scope scope = sas.scope()) {
            if (error != null) {
                sas.span.setAttribute(ERROR_TYPE, errorType != null ? errorType : error.getClass().getName());
                sas.span.setStatus(StatusCode.ERROR, error.getMessage());
                sas.span.recordException(error);
                if (error instanceof McpException mcpException) {
                    sas.span.setAttribute(RPC_RESPONSE_STATUS_CODE, String.valueOf(mcpException.errorCode()));
                }
            } else if (errorType != null) {
                sas.span.setAttribute(ERROR_TYPE, errorType);
                sas.span.setStatus(StatusCode.ERROR);
            }
        } finally {
            sas.span.end();
        }
    }
}