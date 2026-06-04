package org.acme.tools;

import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.service.tool.search.ToolSearchStrategy;

@ApplicationScoped
public class FixedToolSearchStrategySupplier implements Supplier<ToolSearchStrategy> {

    @Override
    public ToolSearchStrategy get() {
        return new FixedToolSearchStrategy();
    }
}
