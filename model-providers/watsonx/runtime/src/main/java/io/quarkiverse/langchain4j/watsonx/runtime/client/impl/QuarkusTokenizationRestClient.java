package io.quarkiverse.langchain4j.watsonx.runtime.client.impl;

import static io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils.retryOn;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.jboss.resteasy.reactive.client.api.LoggingScope;

import com.ibm.watsonx.ai.tokenization.TokenizationRequest;
import com.ibm.watsonx.ai.tokenization.TokenizationResponse;
import com.ibm.watsonx.ai.tokenization.TokenizationRestClient;

import io.quarkiverse.langchain4j.watsonx.runtime.client.QuarkusRestClientConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.client.TokenizationRestApi;
import io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils;
import io.quarkiverse.langchain4j.watsonx.runtime.client.filter.BearerTokenHeaderFactory;
import io.quarkiverse.langchain4j.watsonx.runtime.client.logger.WatsonxClientLogger;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public final class QuarkusTokenizationRestClient extends TokenizationRestClient {

    private final TokenizationRestApi client;

    QuarkusTokenizationRestClient(Builder builder) {
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

            client = restClientBuilder.build(TokenizationRestApi.class);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public TokenizationResponse tokenize(String transactionId, TokenizationRequest request) {
        var requestId = UUID.randomUUID().toString();
        return retryOn(requestId, new Callable<TokenizationResponse>() {
            @Override
            public TokenizationResponse call() throws Exception {
                return client.tokenize(requestId, transactionId, version, request);
            }
        });
    }

    @Override
    public CompletableFuture<TokenizationResponse> asyncTokenize(String transactionId, TokenizationRequest request) {
        return client.asyncTokenize(UUID.randomUUID().toString(), transactionId, version, request)
                .onFailure(WatsonxRestClientUtils::shouldRetry).retry().atMost(10)
                .subscribeAsCompletionStage();
    }

    public static final class QuarkusTokenizationRestClientBuilderFactory implements TokenizationRestClientBuilderFactory {
        @Override
        public Builder get() {
            return new QuarkusTokenizationRestClient.Builder();
        }
    }

    static final class Builder extends TokenizationRestClient.Builder {
        @Override
        public TokenizationRestClient build() {
            return new QuarkusTokenizationRestClient(this);
        }
    }
}
