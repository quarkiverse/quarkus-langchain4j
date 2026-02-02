package io.quarkiverse.langchain4j.sample.assistant.service;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.service.tool.ToolProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.awt.image.AreaAveragingScaleFilter;
import java.util.ArrayList;
import java.util.function.Supplier;

@ApplicationScoped
public class McpToolProviderSupplier implements Supplier<ToolProvider> {

    private final McpToolProvider mcpToolProvider;

    public McpToolProviderSupplier() {
        this.mcpToolProvider = McpToolProvider.builder().mcpClients(new ArrayList<>()).build();
    }

    @Override
    public ToolProvider get() {
        return mcpToolProvider;
    }

    @Produces
    @ApplicationScoped
    public McpToolProvider mcpToolProvider() {
        return mcpToolProvider;
    }
}
