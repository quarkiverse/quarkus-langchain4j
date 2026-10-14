package io.quarkiverse.langchain4j.mcp.runtime;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.net.ssl.SSLContext;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpClientListener;
import dev.langchain4j.mcp.client.McpHeadersSupplier;
import dev.langchain4j.mcp.client.McpMetaSupplier;
import dev.langchain4j.mcp.client.McpRoot;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.mcp.client.transport.websocket.WebSocketMcpTransport;
import dev.langchain4j.mcp.registryclient.DefaultMcpRegistryClient;
import dev.langchain4j.mcp.registryclient.McpRegistryClient;
import dev.langchain4j.service.tool.ToolProvider;
import io.quarkiverse.langchain4j.jaxrsclient.JaxRsHttpClientBuilder;
import io.quarkiverse.langchain4j.mcp.runtime.config.*;
import io.quarkiverse.langchain4j.mcp.runtime.http.McpRequestSender;
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

    /**
     * The default of langchain4j, which isn't configurable in Quarkus.
     */
    private static final int MULTI_ROUND_TRIP_MAX_RETRIES = 3;

    private static final Duration TRACING_MAX_SPAN_AGE_WITHOUT_TIMEOUT = Duration.ofHours(1);

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
            boolean addMetrics,
            boolean hasResourceUpdatedObserver,
            RuntimeValue<Boolean> openTelemetryEnabled) {
        return new Function<>() {
            @Override
            public McpClient apply(SyntheticCreationalContext<McpClient> context) {
                McpTransport transport;
                McpClientRuntimeConfig runtimeConfig = mcpRuntimeConfiguration.getValue().clients().get(key);
                if (runtimeConfig == null) {
                    throw new ConfigurationException(
                            "MCP client configuration named " + key + " has no runtime configuration");
                }
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
                        streamableBuilder.subsidiaryChannel(runtimeConfig.subsidiaryChannel());
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
                if (hasResourceUpdatedObserver) {
                    builder.onResourceUpdated(new McpResourceUpdatedHandler());
                }
                addObservability(builder, key, addMetrics,
                        openTelemetryEnabled != null &&
                                openTelemetryEnabled.getValue() &&
                                mcpRuntimeConfiguration.getValue().tracingEnabled(),
                        mcpTransportType, runtimeConfig.url().orElse(null), transport, tracingMaxSpanAge(runtimeConfig),
                        vertx);
                runtimeConfig.protocolVersion().ifPresent(builder::protocolVersion);
                builder.subscribeToToolListChanges(runtimeConfig.subscribeToToolListChanges());
                builder.subscribeToPromptListChanges(runtimeConfig.subscribeToPromptListChanges());
                builder.subscribeToResourceListChanges(runtimeConfig.subscribeToResourceListChanges());
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
                        .cacheResourceList(runtimeConfig.cacheResourceList().orElse(true))
                        .cachePromptList(runtimeConfig.cachePromptList().orElse(true))
                        .autoHealthCheck(runtimeConfig.autoHealthCheck())
                        .autoHealthCheckInterval(runtimeConfig.autoHealthCheckInterval())
                        .build();
                shutdown.addShutdownTask(client::close);
                return client;
            }
        };
    }

    private void addObservability(DefaultMcpClient.Builder builder, String key, boolean addMetrics, boolean addTracing,
            McpTransportType transportType, String url, McpTransport transport, Duration tracingMaxSpanAge,
            Supplier<Vertx> vertx) {
        if (addMetrics) {
            builder.addListener(createMetricsListener(key));
        }
        if (addTracing) {
            Supplier<String> sessionIdSupplier = transport instanceof QuarkusStreamableHttpMcpTransport httpTransport
                    ? httpTransport::mcpSessionId
                    : null;
            Object tracingListener = createTracingListener(transportType,
                    transportType == McpTransportType.STDIO ? null : url, sessionIdSupplier, tracingMaxSpanAge, vertx.get());
            // the tracing listener also propagates the trace context of its spans in the _meta of MCP messages
            builder.addListener((McpClientListener) tracingListener);
            builder.metaSupplier((McpMetaSupplier) tracingListener);
            // and makes the spans of the HTTP requests carrying the messages children of the spans of the messages
            if (transport instanceof QuarkusStreamableHttpMcpTransport httpTransport) {
                httpTransport.setRequestSender((McpRequestSender) tracingListener);
            }
        }
    }

    private McpClientListener createMetricsListener(String key) {
        try {
            // avoid direct dependency on the MetricsMcpListener class, it would break native
            // compilation if the optional Micrometer dependency isn't present
            return (McpClientListener) Thread.currentThread()
                    .getContextClassLoader().loadClass("io.quarkiverse.langchain4j.mcp.runtime.MetricsMcpListener")
                    .getConstructor(String.class).newInstance(key);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * How long an MCP span may stay in progress before the tracing listener ends it as abandoned: longer than any
     * operation of the client can take. An operation can take its timeout once for the request and once for each
     * multi round-trip retry.
     */
    static Duration tracingMaxSpanAge(McpClientRuntimeConfig config) {
        // the prompts and initialization timeouts aren't configurable, the defaults of langchain4j are 60s and 30s
        Duration longestTimeout = Duration.ofSeconds(60);
        for (Duration timeout : List.of(config.toolExecutionTimeout(), config.resourcesTimeout())) {
            if (timeout.isZero()) {
                // no timeout
                return TRACING_MAX_SPAN_AGE_WITHOUT_TIMEOUT;
            }
            if (timeout.compareTo(longestTimeout) > 0) {
                longestTimeout = timeout;
            }
        }
        return longestTimeout.multipliedBy(1 + MULTI_ROUND_TRIP_MAX_RETRIES).plus(Duration.ofMinutes(1));
    }

    private Object createTracingListener(McpTransportType transportType, String url, Supplier<String> sessionIdSupplier,
            Duration maxSpanAge, Vertx vertx) {
        try {
            // avoid direct dependency on the TracingMcpClientListener class, it would break native
            // compilation if the optional OpenTelemetry dependency isn't present
            return Thread.currentThread()
                    .getContextClassLoader()
                    .loadClass("io.quarkiverse.langchain4j.mcp.runtime.TracingMcpClientListener")
                    .getConstructor(McpTransportType.class, String.class, Supplier.class, Duration.class, Vertx.class)
                    .newInstance(transportType, url, sessionIdSupplier, maxSpanAge, vertx);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Function<SyntheticCreationalContext<ToolProvider>, ToolProvider> toolProviderFunction(
            Set<String> mcpClientNames) {
        return new Function<>() {
            @Override
            public ToolProvider apply(SyntheticCreationalContext<ToolProvider> context) {
                Map<String, McpClient> clients = new HashMap<>();
                for (String mcpClientName : mcpClientNames) {
                    McpClientName.Literal qualifier = McpClientName.Literal.of(mcpClientName);
                    clients.put(mcpClientName, context.getInjectedReference(McpClient.class, qualifier));
                }
                boolean exposeResourcesAsTools = mcpRuntimeConfiguration.getValue().exposeResourcesAsTools().orElse(false);
                return new QuarkusMcpToolProvider(clients, exposeResourcesAsTools);
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
