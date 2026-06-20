package io.quarkiverse.langchain4j.sample.assistant.service;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.util.ArrayList;

@ApplicationScoped
public class McpToolProviderSupplier implements ToolProvider {

    private final McpToolProvider mcpToolProvider;

    public McpToolProviderSupplier() {
        this.mcpToolProvider = McpToolProvider.builder().mcpClients(new ArrayList<>()).build();
    }

    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        return mcpToolProvider.provideTools(request);
    }

    @Produces
    @ApplicationScoped
    public McpToolProvider mcpToolProvider() {
        return mcpToolProvider;
    }
}
