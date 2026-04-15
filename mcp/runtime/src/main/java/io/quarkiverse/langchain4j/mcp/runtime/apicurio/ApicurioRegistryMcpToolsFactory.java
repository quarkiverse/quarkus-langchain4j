package io.quarkiverse.langchain4j.mcp.runtime.apicurio;

import dev.langchain4j.mcp.McpToolProvider;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.vertx.core.Vertx;

/**
 * Factory for creating {@link ApicurioRegistryMcpTools} instances.
 * This class isolates the Apicurio Registry SDK imports so that the
 * {@link io.quarkiverse.langchain4j.mcp.runtime.McpRecorder} can reference
 * it via reflection, avoiding class-loading failures when the SDK is not
 * on the classpath.
 */
public class ApicurioRegistryMcpToolsFactory {

    private ApicurioRegistryMcpToolsFactory() {
    }

    public static ApicurioRegistryMcpTools create(String registryUrl, String authToken,
            McpToolProvider toolProvider, Vertx vertx) {
        RegistryClientOptions options = RegistryClientOptions.create(registryUrl);
        RegistryClient registryClient = RegistryClientFactory.create(options);
        return new ApicurioRegistryMcpTools(registryClient, toolProvider, vertx);
    }
}
