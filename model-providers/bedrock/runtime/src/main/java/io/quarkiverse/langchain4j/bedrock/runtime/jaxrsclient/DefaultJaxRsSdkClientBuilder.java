package io.quarkiverse.langchain4j.bedrock.runtime.jaxrsclient;

import static software.amazon.awssdk.http.SdkHttpConfigurationOption.CONNECTION_TIMEOUT;
import static software.amazon.awssdk.http.SdkHttpConfigurationOption.READ_TIMEOUT;
import static software.amazon.awssdk.utils.NumericUtils.saturatedCast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import jakarta.ws.rs.client.Client;

import org.jboss.resteasy.reactive.client.TlsConfig;
import org.jboss.resteasy.reactive.client.api.QuarkusRestClientProperties;
import org.jboss.resteasy.reactive.client.impl.ClientBuilderImpl;

import io.quarkus.rest.client.reactive.runtime.ProxyAddressUtil;
import io.quarkus.tls.TlsConfiguration;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.SSLOptions;
import io.vertx.core.net.TrustOptions;
import software.amazon.awssdk.http.SdkHttpConfigurationOption;
import software.amazon.awssdk.utils.AttributeMap;
import software.amazon.awssdk.utils.Validate;

public class DefaultJaxRsSdkClientBuilder implements JaxRsSdkClientBuilder {
    private static final String NONE = "none";

    private final AttributeMap.Builder standardOptions = AttributeMap.builder();
    private String proxyAddress;
    private String proxyUser;
    private String proxyPassword;
    private String nonProxyHosts;
    private Boolean disableContextualErrorMessages;
    private Boolean verifyHost;
    private String hostnameVerifier;
    private String trustStore;
    private String trustStorePassword;
    private String trustStoreType;
    private String keyStore;
    private String keyStorePassword;
    private String keyStoreType;
    private TlsConfiguration tlsConfig;

    @Override
    public JaxRsSdkClientBuilder connectionTimeout(final Duration timeout) {
        Validate.isPositive(timeout, "connectionTimeout");
        standardOptions.put(SdkHttpConfigurationOption.CONNECTION_TIMEOUT, timeout);
        return this;
    }

    @Override
    public JaxRsSdkClientBuilder readTimeout(final Duration readTimeout) {
        Validate.isNotNegative(readTimeout, "readTimeout");
        standardOptions.put(SdkHttpConfigurationOption.READ_TIMEOUT, readTimeout);
        return this;
    }

    @Override
    public JaxRsSdkClientBuilder proxyAddress(final String proxyAddress) {
        this.proxyAddress = proxyAddress;
        return this;
    }

    @Override
    public JaxRsSdkClientBuilder proxyUser(final String proxyUser) {
        this.proxyUser = proxyUser;
        return this;
    }

    @Override
    public JaxRsSdkClientBuilder proxyPassword(final String proxyPassword) {
        this.proxyPassword = proxyPassword;
        return this;
    }

    @Override
    public JaxRsSdkClientBuilder nonProxyHosts(final String nonProxyHosts) {
        this.nonProxyHosts = nonProxyHosts;
        return this;
    }

    @Override
    public JaxRsSdkClientBuilder disableContextualErrorMessages(final Boolean disableContextualErrorMessages) {
        this.disableContextualErrorMessages = disableContextualErrorMessages;
        return this;
    }

    @Override
    public JaxRsSdkClientBuilder connectionTTL(final int connectionTTL) {
        Validate.isNotNegative(connectionTTL, "connectionTTL");
        standardOptions.put(SdkHttpConfigurationOption.CONNECTION_TIME_TO_LIVE, Duration.ofMillis(connectionTTL));
        return this;
    }

    @Override
    public JaxRsSdkClientBuilder connectionPoolSize(final int connectionPoolSize) {
        Validate.isNotNegative(connectionPoolSize, "connectionPoolSize");
        standardOptions.put(SdkHttpConfigurationOption.MAX_CONNECTIONS, connectionPoolSize);
        return this;
    }

    @Override
    public JaxRsSdkClientBuilder keepAliveEnabled(final boolean keepAliveEnabled) {
        standardOptions.put(SdkHttpConfigurationOption.TCP_KEEPALIVE, keepAliveEnabled);
        return this;
    }

    @Override
    public JaxRsSdkClientBuilder hostnameVerifier(final String hostnameVerifier) {
        this.hostnameVerifier = hostnameVerifier;
        return this;
    }

    @Override
    public JaxRsSdkClientBuilder verifyHost(final boolean verifyHost) {
        this.verifyHost = verifyHost;
        return this;
    }

    @Override
    public JaxRsSdkClientBuilder trustStore(final String trustStore) {
        this.trustStore = trustStore;
        return this;
    }

    @Override
    public JaxRsSdkClientBuilder trustStorePassword(final String trustStorePassword) {
        this.trustStorePassword = trustStorePassword;
        return this;
    }

    @Override
    public JaxRsSdkClientBuilder trustStoreType(final String trustStoreType) {
        this.trustStoreType = trustStoreType;
        return this;
    }

    @Override
    public JaxRsSdkClientBuilder keyStore(final String keyStore) {
        this.keyStore = keyStore;
        return this;
    }

    @Override
    public JaxRsSdkClientBuilder keyStorePassword(final String keyStorePassword) {
        this.keyStorePassword = keyStorePassword;
        return this;
    }

    @Override
    public JaxRsSdkClientBuilder keyStoreType(final String keyStoreType) {
        this.keyStoreType = keyStoreType;
        return this;
    }

    @Override
    public JaxRsSdkClientBuilder tlsConfig(final TlsConfiguration tlsConfig) {
        this.tlsConfig = tlsConfig;
        return this;
    }

    public Client buildClientWithDefaults(final AttributeMap serviceDefaults) {
        ClientBuilderImpl builder = new ClientBuilderImpl();
        final AttributeMap attributes = standardOptions.build().merge(serviceDefaults);

        builder.connectTimeout(saturatedCast(attributes.get(CONNECTION_TIMEOUT).toMillis()), TimeUnit.MILLISECONDS);
        builder.readTimeout(saturatedCast(attributes.get(READ_TIMEOUT).toMillis()), TimeUnit.MILLISECONDS);
        configureProxy(builder);

        if (disableContextualErrorMessages != null) {
            builder.property(QuarkusRestClientProperties.DISABLE_CONTEXTUAL_ERROR_MESSAGES, disableContextualErrorMessages);
        }

        final Duration ttl = attributes.get(SdkHttpConfigurationOption.CONNECTION_TIME_TO_LIVE);
        if (ttl != null) {
            builder.property(QuarkusRestClientProperties.CONNECTION_TTL, saturatedCast(ttl.toMillis()));
        }

        final Integer connectionPoolSize = attributes.get(SdkHttpConfigurationOption.MAX_CONNECTIONS);
        if (connectionPoolSize != null) {
            builder.property(QuarkusRestClientProperties.CONNECTION_POOL_SIZE, connectionPoolSize);
        }

        final Boolean keepAlive = attributes.get(SdkHttpConfigurationOption.TCP_KEEPALIVE);
        if (keepAlive != null) {
            builder.property(QuarkusRestClientProperties.KEEP_ALIVE_ENABLED, keepAlive);
        }

        if (tlsConfig != null) {
            builder.tlsConfig(new TlsConfig() {
                @Override
                public KeyStore getKeyStore() {
                    return tlsConfig.getKeyStore();
                }

                @Override
                public KeyCertOptions getKeyStoreOptions() {
                    return tlsConfig.getKeyStoreOptions();
                }

                @Override
                public KeyStore getTrustStore() {
                    return tlsConfig.getTrustStore();
                }

                @Override
                public TrustOptions getTrustStoreOptions() {
                    return tlsConfig.getTrustStoreOptions();
                }

                @Override
                public SSLOptions getSSLOptions() {
                    return tlsConfig.getSSLOptions();
                }

                @Override
                public SSLContext createSSLContext() throws Exception {
                    return tlsConfig.createSSLContext();
                }

                @Override
                public Optional<String> getHostnameVerificationAlgorithm() {
                    return tlsConfig.getHostnameVerificationAlgorithm();
                }

                @Override
                public boolean usesSni() {
                    return tlsConfig.usesSni();
                }

                @Override
                public boolean isTrustAll() {
                    return tlsConfig.isTrustAll();
                }

                // TODO: when we bump to the next LTS, this needs to be implemented properly
                //for the time being it exists only to make the module compile against the SNAPSHOT version of Quarkus
                public Optional<String> getName() {
                    throw new IllegalStateException("this should not be called");
                }
            });
        } else {
            configureTLSFromProperties(builder);
        }

        return builder.build();
    }

    private void configureProxy(ClientBuilderImpl builder) {
        if (proxyAddress != null) {
            if (proxyAddress.equals(NONE)) {
                builder.proxy(NONE, 0);
            } else {
                final ProxyAddressUtil.HostAndPort hostAndPort = ProxyAddressUtil.parseAddress(proxyAddress);
                builder.proxy(hostAndPort.host, hostAndPort.port);

                if (proxyUser != null) {
                    builder.proxyUser(proxyUser);
                }
                if (proxyPassword != null) {
                    builder.proxyPassword(proxyPassword);
                }
                if (nonProxyHosts != null) {
                    builder.nonProxyHosts(nonProxyHosts);
                }
            }
        }
    }

    private void configureTLSFromProperties(ClientBuilderImpl builder) {
        if (trustStore != null && !NONE.equals(trustStore)) {
            registerTrustStore(builder, trustStore, trustStorePassword, trustStoreType);
        }

        if (keyStore != null && !NONE.equals(keyStore)) {
            registerKeyStore(builder, keyStore, keyStorePassword, keyStoreType);
        }

        if (hostnameVerifier != null) {
            registerHostnameVerifier(builder, hostnameVerifier);
        }

        if (verifyHost != null) {
            builder.verifyHost(verifyHost);
        }
    }

    private void registerHostnameVerifier(ClientBuilderImpl builder, String verifier) {
        try {
            Class<?> verifierClass = Thread.currentThread().getContextClassLoader().loadClass(verifier);
            builder.hostnameVerifier((HostnameVerifier) verifierClass.getDeclaredConstructor().newInstance());
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(
                    "Could not find a public, no-argument constructor for the hostname verifier class " + verifier, e);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Could not find hostname verifier class " + verifier, e);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalArgumentException(
                    "Failed to instantiate hostname verifier class " + verifier
                            + ". Make sure it has a public, no-argument constructor",
                    e);
        } catch (ClassCastException e) {
            throw new IllegalArgumentException(
                    "The provided hostname verifier " + verifier + " is not an instance of HostnameVerifier",
                    e);
        }
    }

    private void registerKeyStore(ClientBuilderImpl builder, String storePath, String password, String storeType) {
        try {
            KeyStore ks = loadKeyStore(storePath, password, storeType, "keystore");
            builder.keyStore(ks, password);
        } catch (KeyStoreException e) {
            throw new IllegalArgumentException("Failed to initialize trust store from " + storePath, e);
        }
    }

    private void registerTrustStore(ClientBuilderImpl builder, String storePath, String password, String storeType) {
        try {
            KeyStore ts = loadKeyStore(storePath, password, storeType, "truststore");
            builder.trustStore(ts, password.toCharArray());
        } catch (KeyStoreException e) {
            throw new IllegalArgumentException("Failed to initialize trust store from " + storePath, e);
        }
    }

    private KeyStore loadKeyStore(String storePath, String password, String storeType, String name)
            throws KeyStoreException {

        Optional<String> maybeTrustStoreType = Optional.ofNullable(storeType);

        KeyStore ks = KeyStore.getInstance(maybeTrustStoreType.orElse("JKS"));
        if (password == null) {
            throw new IllegalArgumentException("No password provided for " + name);
        }

        try (InputStream input = locateStream(storePath)) {
            ks.load(input, password.toCharArray());
        } catch (IOException | CertificateException | NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Failed to initialize " + name + " from classpath resource " + storePath,
                    e);
        }
        return ks;
    }

    private InputStream locateStream(String path) throws FileNotFoundException {
        if (path.startsWith("classpath:")) {
            path = path.replaceFirst("classpath:", "");
            InputStream resultStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
            if (resultStream == null) {
                resultStream = getClass().getResourceAsStream(path);
            }
            if (resultStream == null) {
                throw new IllegalArgumentException(
                        "Classpath resource " + path + " not found for MicroProfile Rest Client SSL configuration");
            }
            return resultStream;
        } else {
            if (path.startsWith("file:")) {
                path = path.replaceFirst("file:", "");
            }
            File certificateFile = new File(path);
            if (!certificateFile.isFile()) {
                throw new IllegalArgumentException(
                        "Certificate file: " + path + " not found for MicroProfile Rest Client SSL configuration");
            }
            return new FileInputStream(certificateFile);
        }
    }
}
