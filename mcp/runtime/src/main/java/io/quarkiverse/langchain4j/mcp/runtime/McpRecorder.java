package io.quarkiverse.langchain4j.mcp.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.service.tool.ToolProvider;
import io.opentelemetry.api.trace.Tracer;
import io.quarkiverse.langchain4j.mcp.runtime.config.LocalLaunchParams;
import io.quarkiverse.langchain4j.mcp.runtime.config.McpClientRuntimeConfig;
import io.quarkiverse.langchain4j.mcp.runtime.config.McpRuntimeConfiguration;
import io.quarkiverse.langchain4j.mcp.runtime.config.McpTransportType;
import io.quarkiverse.langchain4j.mcp.runtime.http.QuarkusHttpMcpTransport;
import io.quarkiverse.langchain4j.mcp.runtime.http.QuarkusStreamableHttpMcpTransport;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.vertx.core.Vertx;

@Recorder
public class McpRecorder {

    private static final TypeLiteral<Instance<Tracer>> TRACER_TYPE_LITERAL = new TypeLiteral<>() {
    };

    public static Map<String, LocalLaunchParams> claudeConfigContents = Collections.emptyMap();

    private final RuntimeValue<McpRuntimeConfiguration> mcpRuntimeConfiguration;

    private Vertx vertx;

    public McpRecorder(RuntimeValue<McpRuntimeConfiguration> mcpRuntimeConfiguration) {
        this.mcpRuntimeConfiguration = mcpRuntimeConfiguration;
    }

    public void claudeConfigContents(Map<String, LocalLaunchParams> contents) {
        McpRecorder.claudeConfigContents = contents;
    }

    public Supplier<McpClient> mcpClientSupplier(String key,
            McpTransportType mcpTransportType,
            ShutdownContext shutdown,
            Supplier<Vertx> vertx) {
        return new Supplier<McpClient>() {
            @Override
            public McpClient get() {
                McpTransport transport;
                McpClientRuntimeConfig runtimeConfig = mcpRuntimeConfiguration.getValue().clients().get(key);
                transport = switch (mcpTransportType) {
                    case STDIO -> {
                        List<String> command = runtimeConfig.command().orElseThrow(() -> new ConfigurationException(
                                "MCP client configuration named " + key + " is missing the 'command' property"));
                        yield new StdioMcpTransport.Builder()
                                .command(command)
                                .logEvents(runtimeConfig.logResponses().orElse(false))
                                .environment(runtimeConfig.environment())
                                .build();
                    }
                    case HTTP -> new QuarkusHttpMcpTransport.Builder()
                            .sseUrl(runtimeConfig.url().orElseThrow(() -> new ConfigurationException(
                                    "MCP client configuration named " + key + " is missing the 'url' property")))
                            .logRequests(runtimeConfig.logRequests().orElse(false))
                            .logResponses(runtimeConfig.logResponses().orElse(false))
                            .mcpClientName(key)
                            .timeout(runtimeConfig.toolExecutionTimeout())
                            .build();
                    case STREAMABLE_HTTP -> new QuarkusStreamableHttpMcpTransport.Builder()
                            .url(runtimeConfig.url().orElseThrow(() -> new ConfigurationException(
                                    "MCP client configuration named " + key + " is missing the 'url' property")))
                            .logRequests(runtimeConfig.logRequests().orElse(false))
                            .logResponses(runtimeConfig.logResponses().orElse(false))
                            .httpClient(vertx.get().createHttpClient())
                            .mcpClientName(key)
                            .timeout(runtimeConfig.toolExecutionTimeout())
                            .build();
                };
                DefaultMcpClient client = new DefaultMcpClient.Builder()
                        .key(key)
                        .transport(transport)
                        .toolExecutionTimeout(runtimeConfig.toolExecutionTimeout())
                        .resourcesTimeout(runtimeConfig.resourcesTimeout())
                        .pingTimeout(runtimeConfig.pingTimeout())
                        // TODO: it should be possible to choose a log handler class via configuration
                        .logHandler(new QuarkusDefaultMcpLogHandler(key))
                        .build();
                shutdown.addShutdownTask(client::close);
                return client;
            }
        };
    }

    public Function<SyntheticCreationalContext<ToolProvider>, ToolProvider> toolProviderFunction(
            Set<String> mcpClientNames) {
        return new Function<>() {
            @Override
            public ToolProvider apply(SyntheticCreationalContext<ToolProvider> context) {
                List<McpClient> clients = new ArrayList<>();
                for (String mcpClientName : mcpClientNames) {
                    McpClientName.Literal qualifier = McpClientName.Literal.of(mcpClientName);
                    clients.add(context.getInjectedReference(McpClient.class, qualifier));
                }
                return new QuarkusMcpToolProvider(clients, context.getInjectedReference(TRACER_TYPE_LITERAL));
            }
        };
    }
}
