package io.quarkiverse.langchain4j.mcp.runtime;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

import org.jboss.logging.Logger;

import dev.langchain4j.mcp.client.McpCallContext;
import dev.langchain4j.mcp.client.McpClientListener;
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
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

/**
 * An MCP client listener that creates OpenTelemetry spans for MCP client operations.
 */
public class TracingMcpClientListener implements McpClientListener {

    // GenAI semantic convention attribute keys
    private static final AttributeKey<String> GEN_AI_OPERATION_NAME = AttributeKey.stringKey("gen_ai.operation.name");
    private static final AttributeKey<String> GEN_AI_TOOL_NAME = AttributeKey.stringKey("gen_ai.tool.name");
    private static final AttributeKey<String> ERROR_TYPE = AttributeKey.stringKey("error.type");

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
        McpCallToolParams params = (McpCallToolParams) ((McpCallToolRequest) context.message()).getParams();
        String toolName = params.getName();
        Span span = tracer.spanBuilder("execute_tool " + toolName)
                .setAttribute(GEN_AI_OPERATION_NAME, "execute_tool")
                .setAttribute(GEN_AI_TOOL_NAME, toolName)
                .startSpan();
        Scope scope = span.makeCurrent();
        activeSpans.put(context, new SpanAndScope(span, scope));
    }

    @Override
    public void afterExecuteTool(McpCallContext context, ToolExecutionResult result, Map<String, Object> rawResult) {
        SpanAndScope sas = activeSpans.remove(context);
        if (sas == null) {
            log.warn("Unknown call context: " + context.message().getId());
            return;
        }
        if (result != null && result.isError()) {
            sas.span.setAttribute(ERROR_TYPE, "tool_error");
            sas.span.setStatus(StatusCode.ERROR);
        }
        sas.scope.close();
        sas.span.end();
    }

    @Override
    public void onExecuteToolError(McpCallContext context, Throwable error) {
        SpanAndScope sas = activeSpans.remove(context);
        if (sas == null) {
            log.warn("Unknown call context: " + context.message().getId());
            return;
        }
        sas.span.setAttribute(ERROR_TYPE, error.getClass().getName());
        sas.span.setStatus(StatusCode.ERROR, error.getMessage());
        sas.span.recordException(error);
        sas.scope.close();
        sas.span.end();
    }

    @Override
    public void beforeResourceGet(McpCallContext context) {
        McpReadResourceParams params = (McpReadResourceParams) ((McpReadResourceRequest) context.message()).getParams();
        String uri = params.getUri();
        Span span = tracer.spanBuilder("langchain4j.mcp-resources.read")
                .setAttribute("mcp.resource.uri", uri)
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
        McpGetPromptParams params = (McpGetPromptParams) ((McpGetPromptRequest) context.message()).getParams();
        String promptName = params.getName();
        Span span = tracer.spanBuilder("langchain4j.mcp-prompts." + promptName)
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
        SpanAndScope sas = activeSpans.remove(context);
        if (sas == null) {
            log.warn("Unknown call context: " + context.message().getId());
            return;
        }
        if (error != null) {
            sas.span.recordException(error);
        }
        sas.scope.close();
        sas.span.end();
    }
}