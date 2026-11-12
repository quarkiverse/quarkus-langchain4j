package io.quarkiverse.langchain4j.tavily.runtime;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import io.quarkiverse.langchain4j.tavily.QuarkusTavilyWebSearchEngine;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.proxy.ProxyConfiguration;
import io.quarkus.proxy.ProxyConfigurationRegistry;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.tls.TlsConfiguration;
import io.quarkus.tls.TlsConfigurationRegistry;

@Recorder
public class TavilyRecorder {
    private final RuntimeValue<TavilyConfig> runtimeConfig;

    public TavilyRecorder(RuntimeValue<TavilyConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Function<SyntheticCreationalContext<QuarkusTavilyWebSearchEngine>, QuarkusTavilyWebSearchEngine> tavilyEngine() {
        return new Function<>() {
            @Override
            public QuarkusTavilyWebSearchEngine apply(SyntheticCreationalContext<QuarkusTavilyWebSearchEngine> context) {
                TavilyConfig config = runtimeConfig.getValue();
                Proxy proxy = resolveProxy(config, context.getInjectedReference(ProxyConfigurationRegistry.class))
                        .orElse(null);
                TlsConfiguration tlsConfiguration = TlsConfiguration
                        .from(context.getInjectedReference(TlsConfigurationRegistry.class), config.tlsConfigurationName())
                        .orElse(null);
                return new QuarkusTavilyWebSearchEngine(config.baseUrl(),
                        config.apiKey(),
                        config.maxResults(),
                        config.timeout(),
                        config.logRequests().orElse(false),
                        config.logResponses().orElse(false),
                        config.searchDepth(),
                        config.includeAnswer(),
                        config.includeRawContent(),
                        config.includeDomains().orElse(List.of()),
                        config.excludeDomains().orElse(List.of()),
                        proxy,
                        tlsConfiguration);
            }
        };
    }

    private static Optional<Proxy> resolveProxy(TavilyConfig config, ProxyConfigurationRegistry proxyRegistry) {
        Optional<ProxyConfiguration> proxyConfig = proxyRegistry.get(config.proxyConfigurationName());
        if (proxyConfig.isPresent()) {
            ProxyConfiguration pc = proxyConfig.get();
            return Optional.of(new Proxy(Proxy.Type.valueOf(pc.type().name()),
                    new InetSocketAddress(pc.host(), pc.port())));
        }
        return Optional.empty();
    }
}
