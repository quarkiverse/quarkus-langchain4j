package io.quarkiverse.langchain4j.jaxrsclient;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderFactory;

public class JaxRsHttpClientBuilderFactory implements HttpClientBuilderFactory {

    @Override
    public HttpClientBuilder create() {
        return JaxRsHttpClient.builder();
    }
}
