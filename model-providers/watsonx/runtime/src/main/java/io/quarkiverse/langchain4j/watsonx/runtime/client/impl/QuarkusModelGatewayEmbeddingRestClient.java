package io.quarkiverse.langchain4j.watsonx.runtime.client.impl;

import static io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils.retryOn;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.jboss.resteasy.reactive.client.api.LoggingScope;

import com.ibm.watsonx.ai.gateway.embedding.ModelGatewayEmbeddingPayload;
import com.ibm.watsonx.ai.gateway.embedding.ModelGatewayEmbeddingResponse;
import com.ibm.watsonx.ai.gateway.embedding.ModelGatewayEmbeddingRestClient;

import io.quarkiverse.langchain4j.watsonx.runtime.client.GatewayEmbeddingRestApi;
import io.quarkiverse.langchain4j.watsonx.runtime.client.QuarkusRestClientConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.client.filter.BearerTokenHeaderFactory;
import io.quarkiverse.langchain4j.watsonx.runtime.client.logger.WatsonxClientLogger;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public final class QuarkusModelGatewayEmbeddingRestClient extends ModelGatewayEmbeddingRestClient {

    private final GatewayEmbeddingRestApi client;

    QuarkusModelGatewayEmbeddingRestClient(Builder builder) {
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

            client = restClientBuilder.build(GatewayEmbeddingRestApi.class);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ModelGatewayEmbeddingResponse embed(ModelGatewayEmbeddingPayload request) {
        var requestId = UUID.randomUUID().toString();
        return retryOn(requestId, new Callable<ModelGatewayEmbeddingResponse>() {
            @Override
            public ModelGatewayEmbeddingResponse call() throws Exception {
                return client.embed(requestId, version, request);
            }
        });
    }

    public static final class QuarkusModelGatewayEmbeddingRestClientBuilderFactory
            implements ModelGatewayEmbeddingRestClientBuilderFactory {
        @Override
        public Builder get() {
            return new QuarkusModelGatewayEmbeddingRestClient.Builder();
        }
    }

    static final class Builder extends ModelGatewayEmbeddingRestClient.Builder {
        @Override
        public ModelGatewayEmbeddingRestClient build() {
            return new QuarkusModelGatewayEmbeddingRestClient(this);
        }
    }
}
