package io.quarkiverse.langchain4j.watsonx.runtime.client.impl;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.ibm.watsonx.ai.core.auth.ibmcloud.IBMCloudRestClient;
import com.ibm.watsonx.ai.core.auth.ibmcloud.TokenResponse;

import io.quarkiverse.langchain4j.watsonx.runtime.client.IBMCloudAuthRestApi;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public final class QuarkusIBMCloudRestClient extends IBMCloudRestClient {

    private final IBMCloudAuthRestApi client;

    QuarkusIBMCloudRestClient(Builder builder) {
        super(builder);
        try {
            client = QuarkusRestClientBuilder.newBuilder()
                    .baseUrl(baseUrl.toURL())
                    .connectTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                    .readTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                    .build(IBMCloudAuthRestApi.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public TokenResponse token(String apiKey, String grantType) {
        return client.token(apiKey, grantType);
    }

    @Override
    public CompletableFuture<TokenResponse> asyncToken(String apiKey, String grantType) {
        return client.asyncToken(apiKey, grantType).subscribeAsCompletionStage();
    }

    public static final class QuarkusIBMCloudRestClientBuilderFactory implements IBMCloudRestClientBuilderFactory {
        @Override
        public Builder get() {
            return new QuarkusIBMCloudRestClient.Builder();
        }
    }

    static final class Builder extends IBMCloudRestClient.Builder<QuarkusIBMCloudRestClient, Builder> {
        @Override
        public QuarkusIBMCloudRestClient build() {
            return new QuarkusIBMCloudRestClient(this);
        }
    }
}
