package io.quarkiverse.langchain4j.mcp.runtime;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

import org.jboss.logging.Logger;

import dev.langchain4j.mcp.client.McpCallContext;
import dev.langchain4j.mcp.client.McpClientListener;
import dev.langchain4j.mcp.client.McpGetPromptResult;
import dev.langchain4j.mcp.client.McpReadResourceResult;
import dev.langchain4j.mcp.protocol.McpCallToolRequest;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;

/**
 * An MCP client listener that records metrics for MCP client operations using Micrometer.
 */
public class MetricsMcpListener implements McpClientListener {

    private final String mcpClientKey;
    private final CompositeMeterRegistry meterRegistry = Metrics.globalRegistry;
    private final Map<McpCallContext, Long> operationStartTimes = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Logger log = Logger.getLogger(MetricsMcpListener.class);

    enum OperationType {
        TOOL_GET,
        RESOURCE_GET,
        PROMPT_GET
    }

    /**
     * Creates a new MetricsMcpListener for the specified MCP client key.
     *
     * @param mcpClientKey The identifier of the MCP client, it will be used as a tag in the recorded metrics.
     */
    public MetricsMcpListener(String mcpClientKey) {
        this.mcpClientKey = mcpClientKey;
    }

    @Override
    public void beforeExecuteTool(McpCallContext context) {
        operationStartTimes.put(context, System.currentTimeMillis());
    }

    @Override
    public void afterExecuteTool(McpCallContext context, ToolExecutionResult result, Map<String, Object> rawResult) {
        measureCall(context, OperationType.TOOL_GET, result.isError() ? "failure" : "success");
    }

    @Override
    public void onExecuteToolError(McpCallContext context, Throwable error) {
        measureCall(context, OperationType.TOOL_GET, "error");
    }

    @Override
    public void beforeResourceGet(McpCallContext context) {
        operationStartTimes.put(context, System.currentTimeMillis());
    }

    @Override
    public void afterResourceGet(McpCallContext context, McpReadResourceResult result, Map<String, Object> rawResult) {
        measureCall(context, OperationType.RESOURCE_GET, "success");
    }

    @Override
    public void onResourceGetError(McpCallContext context, Throwable error) {
        measureCall(context, OperationType.RESOURCE_GET, "error");
    }

    @Override
    public void beforePromptGet(McpCallContext context) {
        operationStartTimes.put(context, System.currentTimeMillis());
    }

    @Override
    public void afterPromptGet(McpCallContext context, McpGetPromptResult result, Map<String, Object> rawResult) {
        measureCall(context, OperationType.PROMPT_GET, "success");
    }

    @Override
    public void onPromptGetError(McpCallContext context, Throwable error) {
        measureCall(context, OperationType.PROMPT_GET, "error");
    }

    private void measureCall(McpCallContext context, OperationType type, String outcome) {
        Long operationStartTime = operationStartTimes.remove(context);
        if (operationStartTime == null) {
            log.warn("Unknown call context: " + context.message().getId());
        } else {
            long durationMs = System.currentTimeMillis() - operationStartTime;
            switch (type) {
                case TOOL_GET:
                    String toolName = ((McpCallToolRequest) context.message()).getParams().get("name").toString();
                    meterRegistry.timer("mcp.client.tool.call.duration", "mcp_client", mcpClientKey, "outcome", outcome,
                            "tool_name", toolName).record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
                    break;
                case RESOURCE_GET:
                    meterRegistry.timer("mcp.client.resource.get.duration", "mcp_client", mcpClientKey, "outcome", outcome)
                            .record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
                    break;
                case PROMPT_GET:
                    meterRegistry.timer("mcp.client.prompt.get.duration", "mcp_client", mcpClientKey, "outcome", outcome)
                            .record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
                    break;
            }
        }
    }

}
