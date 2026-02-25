package io.quarkiverse.langchain4j.mcp.runtime;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.net.ssl.SSLContext;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpClientListener;
import dev.langchain4j.mcp.client.McpHeadersSupplier;
import dev.langchain4j.mcp.client.McpRoot;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.mcp.client.transport.websocket.WebSocketMcpTransport;
import dev.langchain4j.mcp.registryclient.DefaultMcpRegistryClient;
import dev.langchain4j.mcp.registryclient.McpRegistryClient;
import dev.langchain4j.service.tool.ToolProvider;
import io.opentelemetry.api.trace.Tracer;
import io.quarkiverse.langchain4j.jaxrsclient.JaxRsHttpClientBuilder;
import io.quarkiverse.langchain4j.mcp.runtime.config.*;
import io.quarkiverse.langchain4j.mcp.runtime.http.QuarkusHttpMcpTransport;
import io.quarkiverse.langchain4j.mcp.runtime.http.QuarkusStreamableHttpMcpTransport;
import io.quarkus.arc.Arc;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.tls.TlsConfiguration;
import io.quarkus.tls.TlsConfigurationRegistry;
import io.quarkus.tls.runtime.config.TlsConfigUtils;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;

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

    public Function<SyntheticCreationalContext<McpClient>, McpClient> mcpClientSupplier(String key,
            McpTransportType mcpTransportType,
            ShutdownContext shutdown,
            Supplier<Vertx> vertx,
            boolean addMetrics) {
        return new Function<>() {
            @Override
            public McpClient apply(SyntheticCreationalContext<McpClient> context) {
                McpTransport transport;
                McpClientRuntimeConfig runtimeConfig = mcpRuntimeConfiguration.getValue().clients().get(key);
                List<McpRoot> initialRoots = new ArrayList<>();
                if (runtimeConfig.roots().isPresent()) {
                    for (String kvPair : runtimeConfig.roots().get()) {
                        String[] split = kvPair.split("=");
                        initialRoots.add(new McpRoot(split[0], split[1]));
                    }
                }
                Optional<TlsConfiguration> tlsConfiguration = resolveTlsConfiguration(runtimeConfig.tlsConfigurationName());
                Optional<McpHeadersSupplier> mcpHeadersSupplier = resolveMcpHeadersSupplier();
                transport = switch (mcpTransportType) {
                    case STDIO -> {
                        List<String> command = runtimeConfig.command().orElseThrow(() -> new ConfigurationException(
                                "MCP client configuration named " + key + " is missing the 'command' property"));
                        yield new StdioMcpTransport.Builder()
                                .command(command)
                                .logEvents(runtimeConfig.logResponses().orElse(false))
                                .environment(runtimeConfig.environment())
                                .executorService(context.getInjectedReference(ExecutorService.class))
                                .build();
                    }
                    case HTTP -> {
                        QuarkusHttpMcpTransport.Builder httpBuilder = new QuarkusHttpMcpTransport.Builder()
                                .sseUrl(runtimeConfig.url().orElseThrow(() -> new ConfigurationException(
                                        "MCP client configuration named " + key + " is missing the 'url' property")))
                                .logRequests(runtimeConfig.logRequests().orElse(false))
                                .logResponses(runtimeConfig.logResponses().orElse(false))
                                .tlsConfiguration(tlsConfiguration.orElse(null))
                                .mcpClientName(key)
                                .timeout(runtimeConfig.toolExecutionTimeout());
                        if (!runtimeConfig.header().isEmpty()) {
                            httpBuilder.headers(runtimeConfig.header());
                        }
                        yield httpBuilder.build();
                    }
                    case STREAMABLE_HTTP -> {
                        HttpClientOptions httpClientOptions = new HttpClientOptions();
                        tlsConfiguration.ifPresent(tls -> {
                            TlsConfigUtils.configure(httpClientOptions, tls);
                        });
                        QuarkusStreamableHttpMcpTransport.Builder streamableBuilder = new QuarkusStreamableHttpMcpTransport.Builder()
                                .url(runtimeConfig.url().orElseThrow(() -> new ConfigurationException(
                                        "MCP client configuration named " + key + " is missing the 'url' property")))
                                .logRequests(runtimeConfig.logRequests().orElse(false))
                                .logResponses(runtimeConfig.logResponses().orElse(false))
                                .httpClient(vertx.get().createHttpClient(httpClientOptions))
                                .mcpClientName(key)
                                .timeout(runtimeConfig.toolExecutionTimeout());
                        if (!runtimeConfig.header().isEmpty()) {
                            streamableBuilder.headers(runtimeConfig.header());
                        }
                        mcpHeadersSupplier.ifPresent(streamableBuilder::headers);
                        yield streamableBuilder.build();
                    }
                    case WEBSOCKET -> {
                        SSLContext sslContext = null;
                        if (tlsConfiguration.isPresent()) {
                            try {
                                sslContext = tlsConfiguration.get().createSSLContext();
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                        yield WebSocketMcpTransport.builder()
                                .url(runtimeConfig.url().orElseThrow(() -> new ConfigurationException(
                                        "MCP client configuration named " + key + " is missing the 'url' property")))
                                .logRequests(runtimeConfig.logRequests().orElse(false))
                                .logResponses(runtimeConfig.logResponses().orElse(false))
                                .sslContext(sslContext)
                                .timeout(runtimeConfig.toolExecutionTimeout())
                                .build();
                    }
                };
                DefaultMcpClient.Builder builder = new DefaultMcpClient.Builder();
                if (addMetrics) {
                    addMetrics(builder, key);
                }
                DefaultMcpClient client = builder
                        .key(key)
                        .transport(transport)
                        .toolExecutionTimeout(runtimeConfig.toolExecutionTimeout())
                        .resourcesTimeout(runtimeConfig.resourcesTimeout())
                        .pingTimeout(runtimeConfig.pingTimeout())
                        // TODO: it should be possible to choose a log handler class via configuration
                        .logHandler(new QuarkusDefaultMcpLogHandler(key))
                        .roots(initialRoots)
                        .cacheToolList(runtimeConfig.cacheToolList().orElse(true))
                        .build();
                shutdown.addShutdownTask(client::close);
                return client;
            }
        };
    }

    private void addMetrics(DefaultMcpClient.Builder builder, String key) {
        try {
            // avoid direct dependency on the MetricsMcpListener class, it would break native
            // compilation if the optional Micrometer dependency isn't present
            builder.listener((McpClientListener) Thread.currentThread()
                    .getContextClassLoader().loadClass("io.quarkiverse.langchain4j.mcp.runtime.MetricsMcpListener")
                    .getConstructor(String.class).newInstance(key));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
                boolean exposeResourcesAsTools = mcpRuntimeConfiguration.getValue().exposeResourcesAsTools().orElse(false);
                return new QuarkusMcpToolProvider(clients, context.getInjectedReference(TRACER_TYPE_LITERAL),
                        exposeResourcesAsTools);
            }
        };
    }

    private Optional<McpHeadersSupplier> resolveMcpHeadersSupplier() {
        if (Arc.container() != null) {
            McpHeadersSupplier supplier = Arc.container().select(McpHeadersSupplier.class).orNull();
            return Optional.ofNullable(supplier);
        }
        return Optional.empty();
    }

    private Optional<TlsConfiguration> resolveTlsConfiguration(Optional<String> tlsConfigurationName) {
        if (Arc.container() != null) {
            TlsConfigurationRegistry tlsConfigurationRegistry = Arc.container().select(TlsConfigurationRegistry.class).orNull();
            if (tlsConfigurationRegistry != null) {
                if (tlsConfigurationName.isPresent()) {
                    // explicit TLS config
                    Optional<TlsConfiguration> namedConfig = TlsConfiguration.from(tlsConfigurationRegistry,
                            tlsConfigurationName);
                    if (namedConfig.isEmpty()) {
                        throw new ConfigurationException("TLS configuration '" + tlsConfigurationName.get()
                                + "' was specified, but it does not exist.");
                    }
                    return namedConfig;
                } else {
                    // no explicit TLS config
                    return tlsConfigurationRegistry.getDefault();
                }
            } else {
                if (tlsConfigurationName.isPresent()) {
                    throw new ConfigurationException("TLS configuration '" + tlsConfigurationName.get()
                            + "' was specified, but no TLS configuration registry could be found.");
                }
            }
        }
        return Optional.empty();
    }

    public Supplier<McpRegistryClient> mcpRegistryClientSupplier(String key) {
        return new Supplier<McpRegistryClient>() {
            @Override
            public McpRegistryClient get() {
                McpRegistryClientRuntimeConfig clientRuntimeConfig = mcpRuntimeConfiguration.getValue().registryClients()
                        .get(key);
                JaxRsHttpClientBuilder httpClientBuilder = new JaxRsHttpClientBuilder();
                Optional<TlsConfiguration> tlsConfiguration = resolveTlsConfiguration(
                        clientRuntimeConfig.tlsConfigurationName());
                if (tlsConfiguration.isPresent()) {
                    httpClientBuilder.tlsConfiguration(tlsConfiguration.get());
                }
                httpClientBuilder.connectTimeout(clientRuntimeConfig.connectTimeout().orElse(Duration.ofSeconds(10)));
                httpClientBuilder.readTimeout(clientRuntimeConfig.readTimeout().orElse(Duration.ofSeconds(10)));
                return DefaultMcpRegistryClient.builder()
                        .logResponses(clientRuntimeConfig.logResponses().orElse(false))
                        .logRequests(clientRuntimeConfig.logRequests().orElse(false))
                        .baseUrl(clientRuntimeConfig.baseUrl())
                        .httpClient(httpClientBuilder.build())
                        .build();
            }
        };
    }

}
