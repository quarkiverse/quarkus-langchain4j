package io.quarkiverse.langchain4j.watsonx.runtime.client.impl;

import static io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils.retryOn;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.jboss.resteasy.reactive.client.api.LoggingScope;

import com.ibm.watsonx.ai.gateway.catalog.ModelGatewayCatalogRestClient;
import com.ibm.watsonx.ai.gateway.catalog.ModelGatewayModel;

import io.quarkiverse.langchain4j.watsonx.runtime.client.GatewayCatalogRestApi;
import io.quarkiverse.langchain4j.watsonx.runtime.client.QuarkusRestClientConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.client.filter.BearerTokenHeaderFactory;
import io.quarkiverse.langchain4j.watsonx.runtime.client.logger.WatsonxClientLogger;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public final class QuarkusModelGatewayCatalogRestClient extends ModelGatewayCatalogRestClient {

    private final GatewayCatalogRestApi client;

    QuarkusModelGatewayCatalogRestClient(Builder builder) {
        super(builder);
        try {

            var logCurl = QuarkusRestClientConfig.isLogCurl();
            var clientBuilder = QuarkusRestClientBuilder.newBuilder()
                    .baseUrl(URI.create(baseUrl).toURL())
                    .clientHeadersFactory(new BearerTokenHeaderFactory(authenticator))
                    .connectTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                    .readTimeout(timeout.toSeconds(), TimeUnit.SECONDS);

            if (logRequests || logResponses || logCurl) {
                clientBuilder.loggingScope(LoggingScope.REQUEST_RESPONSE);
                clientBuilder.clientLogger(new WatsonxClientLogger(logRequests, logResponses, logCurl));
            }

            client = clientBuilder.build(GatewayCatalogRestApi.class);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<ModelGatewayModel> listModels() {
        var requestId = UUID.randomUUID().toString();
        return retryOn(requestId, new Callable<List<ModelGatewayModel>>() {
            @Override
            public List<ModelGatewayModel> call() throws Exception {
                return client.listModels(requestId, version).data();
            }
        });
    }

    @Override
    public ModelGatewayModel getModel(String modelId) {
        var requestId = UUID.randomUUID().toString();
        return retryOn(requestId, new Callable<ModelGatewayModel>() {
            @Override
            public ModelGatewayModel call() throws Exception {
                return client.getModel(modelId, requestId, version);
            }
        });
    }

    public static final class QuarkusModelGatewayCatalogRestClientBuilderFactory
            implements ModelGatewayCatalogRestClientBuilderFactory {
        @Override
        public Builder get() {
            return new QuarkusModelGatewayCatalogRestClient.Builder();
        }
    }

    static final class Builder extends ModelGatewayCatalogRestClient.Builder {
        @Override
        public ModelGatewayCatalogRestClient build() {
            return new QuarkusModelGatewayCatalogRestClient(this);
        }
    }
}
