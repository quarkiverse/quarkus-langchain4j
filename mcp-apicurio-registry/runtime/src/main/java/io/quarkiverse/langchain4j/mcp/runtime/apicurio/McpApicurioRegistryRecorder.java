package io.quarkiverse.langchain4j.mcp.runtime.apicurio;

import java.util.function.Function;
import java.util.function.Supplier;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.service.tool.ToolProvider;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.quarkiverse.langchain4j.mcp.runtime.apicurio.config.McpApicurioRegistryRuntimeConfig;
import io.quarkus.arc.ClientProxy;
import io.quarkus.arc.SyntheticCreationalContext;
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

    public Function<SyntheticCreationalContext<ApicurioRegistryMcpTools>, ApicurioRegistryMcpTools> apicurioRegistryMcpToolsFunction(
            Supplier<Vertx> vertx) {
        return new Function<>() {
            @Override
            public ApicurioRegistryMcpTools apply(SyntheticCreationalContext<ApicurioRegistryMcpTools> context) {
                McpApicurioRegistryRuntimeConfig config = runtimeConfig.getValue();

                ToolProvider toolProvider = context.getInjectedReference(ToolProvider.class);
                // The ToolProvider synthetic bean is behind a client proxy typed as ToolProvider.
                // The actual contextual instance is a QuarkusMcpToolProvider (extends McpToolProvider),
                // but the proxy class itself doesn't extend McpToolProvider.
                // Use ClientProxy.unwrap() to get the real instance for the cast.
                Object unwrapped = ClientProxy.unwrap(toolProvider);
                if (!(unwrapped instanceof McpToolProvider)) {
                    throw new ConfigurationException(
                            "The ToolProvider bean must be an McpToolProvider for Apicurio Registry integration");
                }
                McpToolProvider mcpToolProvider = (McpToolProvider) unwrapped;

                RegistryClientOptions options = RegistryClientOptions.create(config.url(), vertx.get());
                RegistryClient registryClient = RegistryClientFactory.create(options);

                return new ApicurioRegistryMcpTools(registryClient, mcpToolProvider, vertx.get());
            }
        };
    }
}
