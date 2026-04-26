package io.quarkiverse.langchain4j.mcp.runtime;

import java.util.List;
import java.util.Map;

import dev.langchain4j.mcp.client.McpCallContext;
import dev.langchain4j.mcp.client.McpClientListener;
import dev.langchain4j.mcp.client.McpGetPromptResult;
import dev.langchain4j.mcp.client.McpReadResourceResult;
import dev.langchain4j.service.tool.ToolExecutionResult;

/**
 * A composite {@link McpClientListener} that delegates to multiple listeners.
 * FIXME: This is just a temporary solution until we have a langchain4j version
 * that supports multiple listeners directly (https://github.com/langchain4j/langchain4j/issues/4904)
 */
class CompositeMcpClientListener implements McpClientListener {

    private final List<McpClientListener> listeners;

    CompositeMcpClientListener(List<McpClientListener> listeners) {
        this.listeners = List.copyOf(listeners);
    }

    @Override
    public void beforeExecuteTool(McpCallContext context) {
        for (McpClientListener listener : listeners) {
            listener.beforeExecuteTool(context);
        }
    }

    @Override
    public void afterExecuteTool(McpCallContext context, ToolExecutionResult result, Map<String, Object> rawResult) {
        for (McpClientListener listener : listeners) {
            listener.afterExecuteTool(context, result, rawResult);
        }
    }

    @Override
    public void onExecuteToolError(McpCallContext context, Throwable error) {
        for (McpClientListener listener : listeners) {
            listener.onExecuteToolError(context, error);
        }
    }

    @Override
    public void beforeResourceGet(McpCallContext context) {
        for (McpClientListener listener : listeners) {
            listener.beforeResourceGet(context);
        }
    }

    @Override
    public void afterResourceGet(McpCallContext context, McpReadResourceResult result, Map<String, Object> rawResult) {
        for (McpClientListener listener : listeners) {
            listener.afterResourceGet(context, result, rawResult);
        }
    }

    @Override
    public void onResourceGetError(McpCallContext context, Throwable error) {
        for (McpClientListener listener : listeners) {
            listener.onResourceGetError(context, error);
        }
    }

    @Override
    public void beforePromptGet(McpCallContext context) {
        for (McpClientListener listener : listeners) {
            listener.beforePromptGet(context);
        }
    }

    @Override
    public void afterPromptGet(McpCallContext context, McpGetPromptResult result, Map<String, Object> rawResult) {
        for (McpClientListener listener : listeners) {
            listener.afterPromptGet(context, result, rawResult);
        }
    }

    @Override
    public void onPromptGetError(McpCallContext context, Throwable error) {
        for (McpClientListener listener : listeners) {
            listener.onPromptGetError(context, error);
        }
    }
}
