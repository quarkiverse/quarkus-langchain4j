package io.quarkiverse.langchain4j.jaxrsclient;

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

    public List<Object> clientProviders() {
        return clientProviders;
    }

    public TlsConfiguration tlsConfiguration() {
        return tlsConfiguration;
    }

    @Override
    public HttpClient build() {
        return new JaxRsHttpClient(this);
    }
}
