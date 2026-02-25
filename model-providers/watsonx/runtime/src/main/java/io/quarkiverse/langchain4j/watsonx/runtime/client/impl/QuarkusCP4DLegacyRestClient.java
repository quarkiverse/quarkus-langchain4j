package io.quarkiverse.langchain4j.watsonx.runtime.client.impl;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import jakarta.json.JsonObject;

import com.ibm.watsonx.ai.core.auth.cp4d.CP4DRestClient;
import com.ibm.watsonx.ai.core.auth.cp4d.TokenRequest;
import com.ibm.watsonx.ai.core.auth.cp4d.TokenResponse;

import io.quarkiverse.langchain4j.watsonx.runtime.client.CP4DAuthRestApi;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public final class QuarkusCP4DLegacyRestClient extends CP4DRestClient {

    private final CP4DAuthRestApi client;

    QuarkusCP4DLegacyRestClient(Builder builder) {
        super(builder);
        try {
            client = QuarkusRestClientBuilder.newBuilder()
                    .baseUrl(baseUrl.toURL())
                    .connectTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                    .readTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                    .build(CP4DAuthRestApi.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public TokenResponse token(TokenRequest request) {
        JsonObject json = client.legacyToken(request);
        return new TokenResponse(json.getString("token"), null, null, null, null, null);
    }

    @Override
    public CompletableFuture<TokenResponse> asyncToken(TokenRequest request) {
        return client.asyncLegacyToken(request)
                .map(json -> new TokenResponse(json.getString("token"), null, null, null, null, null))
                .subscribeAsCompletionStage();
    }

    public static final class QuarkusCP4DLegacyRestClientBuilderFactory implements CP4DLegacyRestClientBuilderFactory {
        @Override
        public Builder get() {
            return new QuarkusCP4DLegacyRestClient.Builder();
        }
    }

    static final class Builder extends CP4DRestClient.Builder<QuarkusCP4DLegacyRestClient, Builder> {
        @Override
        public QuarkusCP4DLegacyRestClient build() {
            return new QuarkusCP4DLegacyRestClient(this);
        }
    }
}
