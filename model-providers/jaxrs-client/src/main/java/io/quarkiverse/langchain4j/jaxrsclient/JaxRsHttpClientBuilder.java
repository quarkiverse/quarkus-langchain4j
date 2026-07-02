package io.quarkiverse.langchain4j.jaxrsclient;

import java.net.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import io.quarkus.tls.TlsConfiguration;

public class JaxRsHttpClientBuilder implements HttpClientBuilder {

    private Duration connectTimeout;
    private Duration readTimeout;
    private final List<Object> clientProviders = new ArrayList<>();
    private TlsConfiguration tlsConfiguration;
    private String proxyHost;
    private int proxyPort;
    private Proxy.Type proxyType;
    private String proxyUsername;
    private String proxyPassword;

    @Override
    public Duration connectTimeout() {
        return connectTimeout;
    }

    @Override
    public HttpClientBuilder connectTimeout(Duration timeout) {
        this.connectTimeout = timeout;
        return this;
    }

    @Override
    public Duration readTimeout() {
        return readTimeout;
    }

    @Override
    public HttpClientBuilder readTimeout(Duration timeout) {
        this.readTimeout = timeout;
        return this;
    }

    public HttpClientBuilder addClientProvider(Object clientProvider) {
        this.clientProviders.add(clientProvider);
        return this;
    }

    public JaxRsHttpClientBuilder tlsConfiguration(TlsConfiguration tlsConfiguration) {
        this.tlsConfiguration = tlsConfiguration;
        return this;
    }

    public JaxRsHttpClientBuilder proxy(String host, int port, Proxy.Type type) {
        this.proxyHost = host;
        this.proxyPort = port;
        this.proxyType = type;
        return this;
    }

    public JaxRsHttpClientBuilder proxyCredentials(String username, String password) {
        this.proxyUsername = username;
        this.proxyPassword = password;
        return this;
    }

    public List<Object> clientProviders() {
        return clientProviders;
    }

    public TlsConfiguration tlsConfiguration() {
        return tlsConfiguration;
    }

    public String proxyHost() {
        return proxyHost;
    }

    public int proxyPort() {
        return proxyPort;
    }

    public Proxy.Type proxyType() {
        return proxyType;
    }

    public String proxyUsername() {
        return proxyUsername;
    }

    public String proxyPassword() {
        return proxyPassword;
    }

    @Override
    public HttpClient build() {
        return new JaxRsHttpClient(this);
    }
}
