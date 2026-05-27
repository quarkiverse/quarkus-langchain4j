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

import io.quarkiverse.langchain4j.bedrock.runtime.client.VertxSdkAsyncHttpClient;
import io.quarkiverse.langchain4j.bedrock.runtime.client.VertxSdkHttpClient;
import io.quarkiverse.langchain4j.bedrock.runtime.config.HttpClientConfig;
import io.quarkiverse.langchain4j.runtime.config.LangChain4jConfig;
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

final class BedrockSdkHttpClientFactory {

    private static final Logger LOG = Logger.getLogger(BedrockSdkHttpClientFactory.class);

    private BedrockSdkHttpClientFactory() {
    }

    static SdkHttpClient createSync(HttpClientConfig modelConfig, HttpClientConfig bedrockConfig,
            LangChain4jConfig rootConfig, Supplier<Vertx> vertx) {
        HttpClientOptions options = buildHttpClientOptions(modelConfig, bedrockConfig, rootConfig);

        Duration readTimeout = firstOrDefault(Duration.ofSeconds(10),
                modelConfig.timeout(), bedrockConfig.timeout(), rootConfig.timeout());

        return new VertxSdkHttpClient(vertx.get().createHttpClient(options), readTimeout);
    }

    static SdkAsyncHttpClient createAsync(HttpClientConfig modelConfig, HttpClientConfig bedrockConfig,
            LangChain4jConfig rootConfig, Supplier<Vertx> vertx) {
        HttpClientOptions options = buildHttpClientOptions(modelConfig, bedrockConfig, rootConfig);

        return new VertxSdkAsyncHttpClient(vertx.get().createHttpClient(options));
    }

    private static HttpClientOptions buildHttpClientOptions(HttpClientConfig modelConfig, HttpClientConfig bedrockConfig,
            LangChain4jConfig rootConfig) {
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
            LOG.warn("Custom hostname verifier is not supported with the Vert.x HTTP client. " +
                    "Use 'verify-host' instead.");
        }

        configureTls(options, modelConfig, bedrockConfig);
        configureProxy(options, modelConfig, bedrockConfig);

        return options;
    }

    private static void configureProxy(HttpClientOptions options, HttpClientConfig modelConfig,
            HttpClientConfig bedrockConfig) {
        String proxyAddress = firstOrDefault(null, modelConfig.proxyAddress(), bedrockConfig.proxyAddress());
        if (proxyAddress == null) {
            return;
        }

        int lastColonIndex = proxyAddress.lastIndexOf(':');
        if (lastColonIndex <= 0 || lastColonIndex == proxyAddress.length() - 1) {
            throw new RuntimeException("Invalid proxy string. Expected <hostname>:<port>, found '" + proxyAddress + "'");
        }

        String host = proxyAddress.substring(0, lastColonIndex);
        int port;
        try {
            port = Integer.parseInt(proxyAddress.substring(lastColonIndex + 1));
        } catch (NumberFormatException e) {
            throw new RuntimeException("Invalid proxy setting. The port is not a number in '" + proxyAddress + "'", e);
        }

        ProxyOptions proxyOptions = new ProxyOptions()
                .setType(ProxyType.HTTP)
                .setHost(host)
                .setPort(port);

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
                stream = BedrockSdkHttpClientFactory.class.getResourceAsStream(resource);
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
