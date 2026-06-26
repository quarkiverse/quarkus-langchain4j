package io.quarkiverse.langchain4j.mcp.runtime;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.resourcesastools.DefaultMcpResourcesAsToolsPresenter;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import io.quarkiverse.langchain4j.runtime.aiservice.QuarkusToolProviderRequest;

public class QuarkusMcpToolProvider extends McpToolProvider {

    /**
     * We keep a separate mapping of client names despite the key being available through client.key()
     * because calling client.key() may unnecessarily trigger initialization of the client
     * in cases when it's needed.
     */
    private Map<String, McpClient> nameToClient = new HashMap<>();

    QuarkusMcpToolProvider(Map<String, McpClient> nameToClient, boolean exposeResourcesAsTools) {
        super(nameToClient.values().stream().toList(), false,
                AlwaysTrueMcpClientToolSpecificationBiPredicate.INSTANCE,
                Function.identity(),
                exposeResourcesAsTools ? DefaultMcpResourcesAsToolsPresenter.builder().build() : null,
                null, null);
        this.nameToClient = nameToClient;
    }

    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        if (request instanceof QuarkusToolProviderRequest quarkusRequest) {
            if (quarkusRequest.getMcpClientNames() == null) {
                // This means we have no @McpToolBox annotation -> no clients
                return ToolProviderResult.builder().build();
            } else if (quarkusRequest.getMcpClientNames().isEmpty()) {
                // This means we have a @McpToolBox annotation with no arguments -> all clients
                return provideTools(request, AlwaysTrueMcpClientToolSpecificationBiPredicate.INSTANCE);
            } else {
                // This means specific clients were given explicitly... so,
                // limit the request to only these clients
                // use the nameToClient map, avoid calling .key() on the client itself
                // because that may unnecessarily trigger an initialization of it
                List<McpClient> allowedClients = quarkusRequest.getMcpClientNames().stream()
                        .map(nameToClient::get)
                        .toList();
                return provideTools(request, AlwaysTrueMcpClientToolSpecificationBiPredicate.INSTANCE, allowedClients);
            }
        } else {
            return provideTools(request, AlwaysTrueMcpClientToolSpecificationBiPredicate.INSTANCE);
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
}
