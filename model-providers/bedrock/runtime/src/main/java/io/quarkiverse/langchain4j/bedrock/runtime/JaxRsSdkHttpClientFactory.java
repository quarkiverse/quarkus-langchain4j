package io.quarkiverse.langchain4j.bedrock.runtime;

import static io.quarkiverse.langchain4j.runtime.OptionalUtil.firstOrDefault;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;

import org.jboss.logging.Logger;

import io.quarkiverse.langchain4j.bedrock.runtime.client.JaxRsSdkHttpClient;
import io.quarkiverse.langchain4j.bedrock.runtime.client.async.VertxSdkAsyncHttpClient;
import io.quarkiverse.langchain4j.bedrock.runtime.config.HttpClientConfig;
import io.quarkiverse.langchain4j.runtime.config.LangChain4jConfig;
import io.quarkus.rest.client.reactive.runtime.ProxyAddressUtil;
import io.quarkus.tls.TlsConfiguration;
import io.quarkus.tls.TlsConfigurationRegistry;
import io.quarkus.tls.runtime.config.TlsConfigUtils;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.PfxOptions;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;

public class JaxRsSdkHttpClientFactory {

    private static final Logger LOG = Logger.getLogger(JaxRsSdkHttpClientFactory.class);

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

    public static SdkAsyncHttpClient createAsync(HttpClientConfig modelConfig, HttpClientConfig bedrockConfig,
            LangChain4jConfig rootConfig, Supplier<Vertx> vertx) {
        HttpClientOptions options = new HttpClientOptions();

        Duration connectTimeout = firstOrDefault(Duration.ofSeconds(3),
                modelConfig.connectTimeout(), bedrockConfig.connectTimeout());
        options.setConnectTimeout((int) connectTimeout.toMillis());

        Duration readTimeout = firstOrDefault(Duration.ofSeconds(10),
                modelConfig.timeout(), bedrockConfig.timeout(), rootConfig.timeout());
        options.setIdleTimeout((int) readTimeout.getSeconds());
        options.setIdleTimeoutUnit(TimeUnit.SECONDS);

        options.setTcpKeepAlive(firstOrDefault(true, modelConfig.keepAliveEnabled(), bedrockConfig.keepAliveEnabled()));

        Integer connectionTtl = firstOrDefault(null, modelConfig.connectionTTL(), bedrockConfig.connectionTTL());
        if (connectionTtl != null) {
            options.setKeepAliveTimeout(connectionTtl / 1000);
        }

        Integer connectionPoolSize = firstOrDefault(null, modelConfig.connectionPoolSize(),
                bedrockConfig.connectionPoolSize());
        if (connectionPoolSize != null) {
            options.setMaxPoolSize(connectionPoolSize);
        }

        Boolean verifyHost = firstOrDefault(null, modelConfig.verifyHost(), bedrockConfig.verifyHost());
        if (verifyHost != null) {
            options.setVerifyHost(verifyHost);
        }

        String hostnameVerifier = firstOrDefault(null, modelConfig.hostnameVerifier(), bedrockConfig.hostnameVerifier());
        if (hostnameVerifier != null) {
            LOG.warn("Custom hostname verifier is not supported with the Vert.x async HTTP client. " +
                    "Use 'verify-host' instead.");
        }

        configureTls(options, modelConfig, bedrockConfig);
        configureProxy(options, modelConfig, bedrockConfig);

        return new VertxSdkAsyncHttpClient(vertx.get().createHttpClient(options));
    }

    private static void configureProxy(HttpClientOptions options, HttpClientConfig modelConfig,
            HttpClientConfig bedrockConfig) {
        String proxyAddress = firstOrDefault(null, modelConfig.proxyAddress(), bedrockConfig.proxyAddress());
        if (proxyAddress == null) {
            return;
        }

        ProxyAddressUtil.HostAndPort hostAndPort = ProxyAddressUtil.parseAddress(proxyAddress);
        ProxyOptions proxyOptions = new ProxyOptions()
                .setType(ProxyType.HTTP)
                .setHost(hostAndPort.host)
                .setPort(hostAndPort.port);

        String proxyUser = firstOrDefault(null, modelConfig.proxyUser(), bedrockConfig.proxyUser());
        if (proxyUser != null) {
            proxyOptions.setUsername(proxyUser);
        }

        String proxyPassword = firstOrDefault(null, modelConfig.proxyPassword(), bedrockConfig.proxyPassword());
        if (proxyPassword != null) {
            proxyOptions.setPassword(proxyPassword);
        }

        options.setProxyOptions(proxyOptions);

        String nonProxyHosts = firstOrDefault(null, modelConfig.nonProxyHosts(), bedrockConfig.nonProxyHosts());
        if (nonProxyHosts != null) {
            for (String nonProxyHost : nonProxyHosts.split(",")) {
                String trimmed = nonProxyHost.trim();
                if (!trimmed.isEmpty()) {
                    options.addNonProxyHost(trimmed);
                }
            }
        }
    }

    private static void configureTls(HttpClientOptions options, HttpClientConfig modelConfig,
            HttpClientConfig bedrockConfig) {
        String tlsConfigName = firstOrDefault(null, modelConfig.tlsConfigurationName(),
                bedrockConfig.tlsConfigurationName());

        if (tlsConfigName != null) {
            Instance<TlsConfigurationRegistry> registries = CDI.current().select(TlsConfigurationRegistry.class);
            if (registries.isResolvable()) {
                Optional<TlsConfiguration> tlsConfig = TlsConfiguration.from(registries.get(),
                        Optional.of(tlsConfigName));
                if (tlsConfig.isPresent()) {
                    TlsConfigUtils.configure(options, tlsConfig.get());
                    return;
                }
            }
        }

        configureTlsFromProperties(options, modelConfig, bedrockConfig);
    }

    private static void configureTlsFromProperties(HttpClientOptions options, HttpClientConfig modelConfig,
            HttpClientConfig bedrockConfig) {
        String trustStore = firstOrDefault(null, modelConfig.trustStore(), bedrockConfig.trustStore());
        if (trustStore != null) {
            String type = firstOrDefault("JKS", modelConfig.trustStoreType(), bedrockConfig.trustStoreType());
            String password = firstOrDefault(null, modelConfig.trustStorePassword(), bedrockConfig.trustStorePassword());
            Buffer storeBuffer = loadStoreAsBuffer(trustStore, "truststore");
            if ("PKCS12".equalsIgnoreCase(type) || "PFX".equalsIgnoreCase(type)) {
                options.setPfxTrustOptions(new PfxOptions().setValue(storeBuffer).setPassword(password));
            } else {
                options.setTrustStoreOptions(new JksOptions().setValue(storeBuffer).setPassword(password));
            }
        }

        String keyStore = firstOrDefault(null, modelConfig.keyStore(), bedrockConfig.keyStore());
        if (keyStore != null) {
            String type = firstOrDefault("JKS", modelConfig.keyStoreType(), bedrockConfig.keyStoreType());
            String password = firstOrDefault(null, modelConfig.keyStorePassword(), bedrockConfig.keyStorePassword());
            Buffer storeBuffer = loadStoreAsBuffer(keyStore, "keystore");
            if ("PKCS12".equalsIgnoreCase(type) || "PFX".equalsIgnoreCase(type)) {
                options.setPfxKeyCertOptions(new PfxOptions().setValue(storeBuffer).setPassword(password));
            } else {
                options.setKeyStoreOptions(new JksOptions().setValue(storeBuffer).setPassword(password));
            }
        }
    }

    private static Buffer loadStoreAsBuffer(String storePath, String name) {
        try (InputStream input = locateStream(storePath)) {
            return Buffer.buffer(input.readAllBytes());
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to load " + name + " from " + storePath, e);
        }
    }

    private static InputStream locateStream(String path) throws FileNotFoundException {
        if (path.startsWith("classpath:")) {
            String resource = path.substring("classpath:".length());
            InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
            if (stream == null) {
                stream = JaxRsSdkHttpClientFactory.class.getResourceAsStream(resource);
            }
            if (stream == null) {
                throw new IllegalArgumentException("Classpath resource " + resource + " not found");
            }
            return stream;
        }

        if (path.startsWith("file:")) {
            path = path.substring("file:".length());
        }
        File file = new File(path);
        if (!file.isFile()) {
            throw new IllegalArgumentException("File " + path + " not found");
        }
        return new FileInputStream(file);
    }
}
