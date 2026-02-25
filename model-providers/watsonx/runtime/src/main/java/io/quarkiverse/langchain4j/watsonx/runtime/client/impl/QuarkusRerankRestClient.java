package io.quarkiverse.langchain4j.watsonx.runtime.client.impl;

import static io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils.retryOn;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.jboss.resteasy.reactive.client.api.LoggingScope;

import com.ibm.watsonx.ai.rerank.RerankRequest;
import com.ibm.watsonx.ai.rerank.RerankResponse;
import com.ibm.watsonx.ai.rerank.RerankRestClient;

import io.quarkiverse.langchain4j.watsonx.runtime.client.QuarkusRestClientConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.client.RerankRestApi;
import io.quarkiverse.langchain4j.watsonx.runtime.client.filter.BearerTokenHeaderFactory;
import io.quarkiverse.langchain4j.watsonx.runtime.client.logger.WatsonxClientLogger;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public final class QuarkusRerankRestClient extends RerankRestClient {

    private final RerankRestApi client;

    QuarkusRerankRestClient(Builder builder) {
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

            client = restClientBuilder.build(RerankRestApi.class);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public RerankResponse rerank(String transactionId, RerankRequest request) {
        var requestId = UUID.randomUUID().toString();
        return retryOn(requestId, new Callable<RerankResponse>() {
            @Override
            public RerankResponse call() throws Exception {
                return client.rerank(requestId, transactionId, version, request);
            }
        });
    }

    public static final class QuarkusRerankRestClientBuilderFactory implements RerankRestClientBuilderFactory {
        @Override
        public Builder get() {
            return new QuarkusRerankRestClient.Builder();
        }
    }

    static final class Builder extends RerankRestClient.Builder {
        @Override
        public RerankRestClient build() {
            return new QuarkusRerankRestClient(this);
        }
    }
}
