package io.quarkiverse.langchain4j.watsonx.runtime.client.impl;

import static io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils.retryOn;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.jboss.resteasy.reactive.client.api.LoggingScope;

import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageGenerationRequest;
import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageResponse;
import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageRestClient;

import io.quarkiverse.langchain4j.watsonx.runtime.client.GatewayImageRestApi;
import io.quarkiverse.langchain4j.watsonx.runtime.client.QuarkusRestClientConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.client.filter.BearerTokenHeaderFactory;
import io.quarkiverse.langchain4j.watsonx.runtime.client.logger.WatsonxClientLogger;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public final class QuarkusModelGatewayImageRestClient extends ModelGatewayImageRestClient {

    private final GatewayImageRestApi client;

    QuarkusModelGatewayImageRestClient(Builder builder) {
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

            client = restClientBuilder.build(GatewayImageRestApi.class);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ModelGatewayImageResponse generate(ModelGatewayImageGenerationRequest request) {
        var requestId = UUID.randomUUID().toString();
        return retryOn(requestId, new Callable<ModelGatewayImageResponse>() {
            @Override
            public ModelGatewayImageResponse call() throws Exception {
                return client.generate(requestId, version, request);
            }
        });
    }

    public static final class QuarkusModelGatewayImageRestClientBuilderFactory
            implements ModelGatewayImageRestClientBuilderFactory {
        @Override
        public Builder get() {
            return new QuarkusModelGatewayImageRestClient.Builder();
        }
    }

    static final class Builder extends ModelGatewayImageRestClient.Builder {
        @Override
        public ModelGatewayImageRestClient build() {
            return new QuarkusModelGatewayImageRestClient(this);
        }
    }
}
