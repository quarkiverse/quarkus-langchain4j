package io.quarkiverse.langchain4j.mcp.runtime.apicurio;

import java.util.function.Supplier;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.service.tool.ToolProvider;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.quarkiverse.langchain4j.mcp.runtime.apicurio.config.McpApicurioRegistryRuntimeConfig;
import io.quarkus.arc.Arc;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.vertx.core.Vertx;

@Recorder
public class McpApicurioRegistryRecorder {

    private final RuntimeValue<McpApicurioRegistryRuntimeConfig> runtimeConfig;

    public McpApicurioRegistryRecorder(RuntimeValue<McpApicurioRegistryRuntimeConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Supplier<Object> apicurioRegistryMcpToolsSupplier(Supplier<Vertx> vertx) {
        return () -> {
            McpApicurioRegistryRuntimeConfig config = runtimeConfig.getValue();

            ToolProvider toolProvider = Arc.container().select(ToolProvider.class).get();
            if (!(toolProvider instanceof McpToolProvider)) {
                throw new ConfigurationException(
                        "The ToolProvider bean must be an McpToolProvider for Apicurio Registry integration");
            }

            RegistryClientOptions options = RegistryClientOptions.create(config.url());
            RegistryClient registryClient = RegistryClientFactory.create(options);

            return new ApicurioRegistryMcpTools(registryClient, (McpToolProvider) toolProvider, vertx.get());
        };
    }
}
