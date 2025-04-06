package io.quarkiverse.langchain4j.bedrock.runtime;

import static io.quarkiverse.langchain4j.runtime.OptionalUtil.firstOrDefault;

import java.time.Duration;
import java.util.Optional;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;

import io.quarkiverse.langchain4j.bedrock.runtime.config.HttpClientConfig;
import io.quarkiverse.langchain4j.bedrock.runtime.jaxrsclient.JaxRsSdkHttpClient;
import io.quarkiverse.langchain4j.bedrock.runtime.jaxrsclient.async.JaxRsSdkAsyncHttpClient;
import io.quarkiverse.langchain4j.runtime.config.LangChain4jConfig;
import io.quarkus.tls.TlsConfiguration;
import io.quarkus.tls.TlsConfigurationRegistry;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;

public class JaxRsSdkHttpClientFactory {

    private JaxRsSdkHttpClientFactory() {
        // hide constructor
    }

    public static SdkHttpClient createSync(final HttpClientConfig modelConfig, final HttpClientConfig bedrockConfig,
            final LangChain4jConfig rootConfig) {
        final JaxRsSdkHttpClient.Builder builder = JaxRsSdkHttpClient.builder();

        builder.connectionTimeout(
                firstOrDefault(Duration.ofSeconds(3), modelConfig.connectTimeout(), bedrockConfig.connectTimeout()));
        builder.readTimeout(
                firstOrDefault(Duration.ofSeconds(10), modelConfig.timeout(), bedrockConfig.timeout(), rootConfig.timeout()));
        builder.proxyAddress(firstOrDefault(null, modelConfig.proxyAddress(), bedrockConfig.proxyAddress()));
        builder.proxyUser(firstOrDefault(null, modelConfig.proxyUser(), bedrockConfig.proxyUser()));
        builder.proxyPassword(firstOrDefault(null, modelConfig.proxyPassword(), bedrockConfig.proxyPassword()));
        builder.nonProxyHosts(firstOrDefault(null, modelConfig.nonProxyHosts(), bedrockConfig.nonProxyHosts()));
        builder.disableContextualErrorMessages(firstOrDefault(false, modelConfig.disableContextualErrorMessages(),
                bedrockConfig.disableContextualErrorMessages()));

        final Integer connectionTtl = firstOrDefault(null, modelConfig.connectionTTL(), bedrockConfig.connectionTTL());
        if (connectionTtl != null) {
            builder.connectionTTL(connectionTtl);
        }

        final Integer connectionPoolSize = firstOrDefault(null, modelConfig.connectionPoolSize(),
                bedrockConfig.connectionPoolSize());
        if (connectionPoolSize != null) {
            builder.connectionPoolSize(connectionPoolSize);
        }

        builder.keepAliveEnabled(firstOrDefault(true, modelConfig.keepAliveEnabled(), bedrockConfig.keepAliveEnabled()));
        builder.hostnameVerifier(firstOrDefault(null, modelConfig.hostnameVerifier(), bedrockConfig.hostnameVerifier()));

        final Boolean verifyHost = firstOrDefault(null, modelConfig.verifyHost(), bedrockConfig.verifyHost());
        if (verifyHost != null) {
            builder.verifyHost(verifyHost);
        }

        builder.trustStore(firstOrDefault(null, modelConfig.trustStore(), bedrockConfig.trustStore()));
        builder.trustStorePassword(firstOrDefault(null, modelConfig.trustStorePassword(), bedrockConfig.trustStorePassword()));
        builder.trustStoreType(firstOrDefault(null, modelConfig.trustStoreType(), bedrockConfig.trustStoreType()));
        builder.keyStore(firstOrDefault(null, modelConfig.keyStore(), bedrockConfig.keyStore()));
        builder.keyStorePassword(firstOrDefault(null, modelConfig.keyStorePassword(), bedrockConfig.keyStorePassword()));
        builder.keyStoreType(firstOrDefault(null, modelConfig.keyStoreType(), bedrockConfig.keyStoreType()));

        final String tlsConfigName = firstOrDefault(null, modelConfig.tlsConfigurationName(),
                bedrockConfig.tlsConfigurationName());

        if (tlsConfigName != null) {
            final Instance<TlsConfigurationRegistry> tlsConfigurationRegistries = CDI.current()
                    .select(TlsConfigurationRegistry.class);
            if (tlsConfigurationRegistries.isResolvable()) {
                final Optional<TlsConfiguration> tlsConfig = TlsConfiguration.from(tlsConfigurationRegistries.get(),
                        Optional.ofNullable(tlsConfigName));

                if (tlsConfig.isPresent()) {
                    builder.tlsConfig(tlsConfig.get());
                }
            }
        }

        return builder.build();
    }

    public static SdkAsyncHttpClient createAsync(final HttpClientConfig modelConfig, final HttpClientConfig bedrockConfig,
            final LangChain4jConfig rootConfig) {
        final JaxRsSdkAsyncHttpClient.Builder builder = JaxRsSdkAsyncHttpClient.builder();

        builder.connectionTimeout(
                firstOrDefault(Duration.ofSeconds(3), modelConfig.connectTimeout(), bedrockConfig.connectTimeout()));
        builder.readTimeout(
                firstOrDefault(Duration.ofSeconds(10), modelConfig.timeout(), bedrockConfig.timeout(), rootConfig.timeout()));
        builder.proxyAddress(firstOrDefault(null, modelConfig.proxyAddress(), bedrockConfig.proxyAddress()));
        builder.proxyUser(firstOrDefault(null, modelConfig.proxyUser(), bedrockConfig.proxyUser()));
        builder.proxyPassword(firstOrDefault(null, modelConfig.proxyPassword(), bedrockConfig.proxyPassword()));
        builder.nonProxyHosts(firstOrDefault(null, modelConfig.nonProxyHosts(), bedrockConfig.nonProxyHosts()));
        builder.disableContextualErrorMessages(firstOrDefault(false, modelConfig.disableContextualErrorMessages(),
                bedrockConfig.disableContextualErrorMessages()));

        final Integer connectionTtl = firstOrDefault(null, modelConfig.connectionTTL(), bedrockConfig.connectionTTL());
        if (connectionTtl != null) {
            builder.connectionTTL(connectionTtl);
        }

        final Integer connectionPoolSize = firstOrDefault(null, modelConfig.connectionPoolSize(),
                bedrockConfig.connectionPoolSize());
        if (connectionPoolSize != null) {
            builder.connectionPoolSize(connectionPoolSize);
        }

        builder.keepAliveEnabled(firstOrDefault(true, modelConfig.keepAliveEnabled(), bedrockConfig.keepAliveEnabled()));
        builder.hostnameVerifier(firstOrDefault(null, modelConfig.hostnameVerifier(), bedrockConfig.hostnameVerifier()));

        final Boolean verifyHost = firstOrDefault(null, modelConfig.verifyHost(), bedrockConfig.verifyHost());
        if (verifyHost != null) {
            builder.verifyHost(verifyHost);
        }

        builder.trustStore(firstOrDefault(null, modelConfig.trustStore(), bedrockConfig.trustStore()));
        builder.trustStorePassword(firstOrDefault(null, modelConfig.trustStorePassword(), bedrockConfig.trustStorePassword()));
        builder.trustStoreType(firstOrDefault(null, modelConfig.trustStoreType(), bedrockConfig.trustStoreType()));
        builder.keyStore(firstOrDefault(null, modelConfig.keyStore(), bedrockConfig.keyStore()));
        builder.keyStorePassword(firstOrDefault(null, modelConfig.keyStorePassword(), bedrockConfig.keyStorePassword()));
        builder.keyStoreType(firstOrDefault(null, modelConfig.keyStoreType(), bedrockConfig.keyStoreType()));

        final String tlsConfigName = firstOrDefault(null, modelConfig.tlsConfigurationName(),
                bedrockConfig.tlsConfigurationName());

        if (tlsConfigName != null) {
            final Instance<TlsConfigurationRegistry> tlsConfigurationRegistries = CDI.current()
                    .select(TlsConfigurationRegistry.class);
            if (tlsConfigurationRegistries.isResolvable()) {
                final Optional<TlsConfiguration> tlsConfig = TlsConfiguration.from(tlsConfigurationRegistries.get(),
                        Optional.ofNullable(tlsConfigName));

                if (tlsConfig.isPresent()) {
                    builder.tlsConfig(tlsConfig.get());
                }
            }
        }

        return builder.build();
    }
}
