package io.quarkiverse.langchain4j.tavily;

import java.time.Duration;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import io.quarkiverse.langchain4j.jaxrsclient.JaxRsHttpClientBuilder;

class TavilyHttpClientBuilder implements HttpClientBuilder {

    private final JaxRsHttpClientBuilder delegate;
    private final boolean logRequests;
    private final boolean logResponses;

    TavilyHttpClientBuilder(JaxRsHttpClientBuilder delegate, boolean logRequests, boolean logResponses) {
        this.delegate = delegate;
        this.logRequests = logRequests;
        this.logResponses = logResponses;
    }

    @Override
    public Duration connectTimeout() {
        return delegate.connectTimeout();
    }

    @Override
    public HttpClientBuilder connectTimeout(Duration timeout) {
        delegate.connectTimeout(timeout);
        return this;
    }

    @Override
    public Duration readTimeout() {
        return delegate.readTimeout();
    }

    @Override
    public HttpClientBuilder readTimeout(Duration timeout) {
        delegate.readTimeout(timeout);
        return this;
    }

    /**
     * The {@link TavilyResultUrlSanitizer} wrapping is temporary, see its Javadoc for what to remove.
     */
    @Override
    public HttpClient build() {
        HttpClient client = delegate.build();
        if (logRequests || logResponses) {
            client = new TavilyLoggingHttpClient(client, logRequests, logResponses);
        }
        return new TavilyResultUrlSanitizer(client);
    }
}
