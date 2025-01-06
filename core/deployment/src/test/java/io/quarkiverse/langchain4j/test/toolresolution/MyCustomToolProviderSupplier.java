package io.quarkiverse.langchain4j.test.toolresolution;

import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import dev.langchain4j.service.tool.ToolProvider;

@ApplicationScoped
public class MyCustomToolProviderSupplier implements Supplier<ToolProvider> {
    @Inject
    MyCustomToolProvider myCustomToolProvider;

    @Override
    public ToolProvider get() {
        return myCustomToolProvider;
    }
}
