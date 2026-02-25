package io.quarkiverse.langchain4j.watsonx.runtime.client.impl;

import static io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils.retryOn;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.jboss.resteasy.reactive.client.api.LoggingScope;

import com.ibm.watsonx.ai.embedding.EmbeddingRequest;
import com.ibm.watsonx.ai.embedding.EmbeddingResponse;
import com.ibm.watsonx.ai.embedding.EmbeddingRestClient;

import io.quarkiverse.langchain4j.watsonx.runtime.client.EmbeddingRestApi;
import io.quarkiverse.langchain4j.watsonx.runtime.client.QuarkusRestClientConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.client.filter.BearerTokenHeaderFactory;
import io.quarkiverse.langchain4j.watsonx.runtime.client.logger.WatsonxClientLogger;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public final class QuarkusEmbeddingRestClient extends EmbeddingRestClient {

    private final EmbeddingRestApi client;

    QuarkusEmbeddingRestClient(Builder builder) {
        super(builder);
        try {

            var logCurl = QuarkusRestClientConfig.isLogCurl();
            var restClientBuilder = QuarkusRestClientBuilder.newBuilder()
                    .baseUrl(URI.create(baseUrl).toURL())
                    .clientHeadersFactory(new BearerTokenHeaderFactory(authenticator))
                    .connectTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                    .readTimeout(timeout.toSeconds(), TimeUnit.SECONDS);

            if (logRequests || logResponses || logCurl) {
                restClientBuilder.loggingScope(LoggingScope.REQUEST_RESPONSE);
                restClientBuilder.clientLogger(new WatsonxClientLogger(logRequests, logResponses, logCurl));
            }

            client = restClientBuilder.build(EmbeddingRestApi.class);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public EmbeddingResponse embedding(String transactionId, EmbeddingRequest embeddingRequest) {
        var requestId = UUID.randomUUID().toString();
        return retryOn(requestId, new Callable<EmbeddingResponse>() {
            @Override
            public EmbeddingResponse call() throws Exception {
                return client.embedding(requestId, transactionId, version, embeddingRequest);
            }
        });
    }

    public static final class QuarkusEmbeddingRestClientBuilderFactory implements EmbeddingRestClientBuilderFactory {
        @Override
        public Builder get() {
            return new QuarkusEmbeddingRestClient.Builder();
        }
    }

    static final class Builder extends EmbeddingRestClient.Builder {
        @Override
        public EmbeddingRestClient build() {
            return new QuarkusEmbeddingRestClient(this);
        }
    }
}
