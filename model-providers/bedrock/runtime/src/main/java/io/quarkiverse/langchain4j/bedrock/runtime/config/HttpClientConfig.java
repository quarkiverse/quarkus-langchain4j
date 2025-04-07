package io.quarkiverse.langchain4j.bedrock.runtime.config;

import java.time.Duration;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;

public interface HttpClientConfig {

    /**
     * Connect Timeout for Bedrock calls
     */
    @ConfigDocDefault("3s")
    Optional<Duration> connectTimeout();

    /**
     * Read Timeout for Bedrock calls
     */
    @ConfigDocDefault("10s")
    Optional<Duration> timeout();

    /**
     * A string value in the form of `<proxyHost>:<proxyPort>` that specifies the HTTP proxy server hostname
     * (or IP address) and port for requests of clients to use.
     */
    Optional<String> proxyAddress();

    /**
     * Proxy username, equivalent to the http.proxy or https.proxy JVM settings.
     */
    Optional<String> proxyUser();

    /**
     * Proxy password, equivalent to the http.proxyPassword or https.proxyPassword JVM settings.
     */
    Optional<String> proxyPassword();

    /**
     * Hosts to access without proxy, similar to the http.nonProxyHosts or https.nonProxyHosts JVM settings.
     * Please note that unlike the JVM settings, this property is empty by default.
     */
    Optional<String> nonProxyHosts();

    /**
     * If true, the REST clients will not provide additional contextual information (like REST client class and method
     * names) when exception occurs during a client invocation.
     */
    @ConfigDocDefault("false")
    Optional<Boolean> disableContextualErrorMessages();

    /**
     * The time in ms for which a connection remains unused in the connection pool before being evicted and closed.
     * A timeout of {@code 0} means there is no timeout.
     */
    Optional<Integer> connectionTTL();

    /**
     * The size of the connection pool for this client.
     */
    Optional<Integer> connectionPoolSize();

    /**
     * If set to false disables the keep alive completely.
     */
    @ConfigDocDefault("true")
    Optional<Boolean> keepAliveEnabled();

    /**
     * The class name of the host name verifier. The class must have a public no-argument constructor.
     */
    Optional<String> hostnameVerifier();

    /**
     * Set whether hostname verification is enabled. Default is enabled.
     * This setting should not be disabled in production as it makes the client vulnerable to MITM attacks.
     */
    Optional<Boolean> verifyHost();

    /**
     * The trust store location. Can point to either a classpath resource or a file.
     */
    Optional<String> trustStore();

    /**
     * The trust store password.
     */
    Optional<String> trustStorePassword();

    /**
     * The type of the trust store. Defaults to "JKS".
     */
    Optional<String> trustStoreType();

    /**
     * The key store location. Can point to either a classpath resource or a file.
     */
    Optional<String> keyStore();

    /**
     * The key store password.
     */
    Optional<String> keyStorePassword();

    /**
     * The type of the key store. Defaults to "JKS".
     */
    Optional<String> keyStoreType();

    /**
     * The name of the TLS configuration to use.
     * <p>
     * If not set and the default TLS configuration is configured ({@code quarkus.tls.*}) then that will be used.
     * If a name is configured, it uses the configuration from {@code quarkus.tls.<name>.*}
     * If a name is configured, but no TLS configuration is found with that name then an error will be thrown.
     */
    Optional<String> tlsConfigurationName();
}
