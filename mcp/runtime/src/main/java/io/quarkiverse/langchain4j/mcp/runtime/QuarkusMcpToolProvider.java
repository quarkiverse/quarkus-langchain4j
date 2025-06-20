package io.quarkiverse.langchain4j.mcp.runtime;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;

import jakarta.enterprise.inject.Instance;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.quarkiverse.langchain4j.runtime.aiservice.QuarkusToolProviderRequest;

public class QuarkusMcpToolProvider extends McpToolProvider {

    QuarkusMcpToolProvider(List<McpClient> mcpClients, Instance<Tracer> tracerInstance) {
        super(mcpClients, false, AlwaysTrueMcpClientToolSpecificationBiPredicate.INSTANCE,
                determineToolWrapper(tracerInstance));
    }

    private static Function<ToolExecutor, ToolExecutor> determineToolWrapper(Instance<Tracer> tracerInstance) {
        if (tracerInstance.isResolvable()) {
            return new SpanToolExecutor(tracerInstance);
        } else {
            return Function.identity();
        }
    }

    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        return provideTools(request, getMcpClientsFilter(request));
    }

    private BiPredicate<McpClient, ToolSpecification> getMcpClientsFilter(ToolProviderRequest request) {
        if (request instanceof QuarkusToolProviderRequest quarkusRequest) {
            return new McpClientKeyFilter(quarkusRequest.getMcpClientNames());
        }
        return AlwaysTrueMcpClientToolSpecificationBiPredicate.INSTANCE;
    }

    private static class McpClientKeyFilter implements BiPredicate<McpClient, ToolSpecification> {
        private final List<String> keys;

        private McpClientKeyFilter(List<String> keys) {
            this.keys = keys;
        }

        @Override
        public boolean test(McpClient mcpClient, ToolSpecification tool) {
            // keys == null means no McpToolBox annotation, so no MCP clients, whereas
            // keys.size() == 0 means all MCP clients
            return keys != null
                    && (keys.isEmpty() || keys.stream().anyMatch(name -> name.equals(mcpClient.key())));
        }
    }

    private static class AlwaysTrueMcpClientToolSpecificationBiPredicate
            implements BiPredicate<McpClient, ToolSpecification> {

        private static final AlwaysTrueMcpClientToolSpecificationBiPredicate INSTANCE = new AlwaysTrueMcpClientToolSpecificationBiPredicate();

        private AlwaysTrueMcpClientToolSpecificationBiPredicate() {
        }

        @Override
        public boolean test(McpClient mcpClient, ToolSpecification toolSpecification) {
            return true;
        }
    }

    private static class SpanToolExecutor implements Function<ToolExecutor, ToolExecutor> {
        private final Instance<Tracer> tracerInstance;

        public SpanToolExecutor(Instance<Tracer> tracerInstance) {
            this.tracerInstance = tracerInstance;
        }

        @Override
        public ToolExecutor apply(ToolExecutor toolExecutor) {
            return new ToolExecutor() {
                @Override
                public String execute(ToolExecutionRequest toolExecutionRequest, Object memoryId) {
                    Span span = tracerInstance.get().spanBuilder("langchain4j.mcp-tools." + toolExecutionRequest.name())
                            .startSpan();
                    try (Scope scope = span.makeCurrent()) {
                        return toolExecutor.execute(toolExecutionRequest, memoryId);
                    } catch (Throwable t) {
                        span.recordException(t);
                        throw t;
                    } finally {
                        span.end();
                    }
                }
            };
        }
    }
}
