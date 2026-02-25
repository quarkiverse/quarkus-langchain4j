package io.quarkiverse.langchain4j.watsonx.runtime.client.impl;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.ibm.watsonx.ai.core.auth.cp4d.CP4DRestClient;
import com.ibm.watsonx.ai.core.auth.cp4d.TokenRequest;
import com.ibm.watsonx.ai.core.auth.cp4d.TokenResponse;

import io.quarkiverse.langchain4j.watsonx.runtime.client.CP4DAuthRestApi;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public final class QuarkusCP4DIAMRestClient extends CP4DRestClient {

    private final CP4DAuthRestApi client;

    QuarkusCP4DIAMRestClient(Builder builder) {
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
        var tokenResponse = client.iamIdentityToken("password", request.username(), request.password(), "openid");
        var json = client.iamValidationRequest(request.username(), tokenResponse.accessToken());
        return new TokenResponse(
                json.containsKey("accessToken") ? json.getString("accessToken") : json.getString("token"),
                tokenResponse.refreshToken(),
                tokenResponse.tokenType(),
                tokenResponse.expiresIn(),
                tokenResponse.expiration(),
                tokenResponse.scope());
    }

    @Override
    public CompletableFuture<TokenResponse> asyncToken(TokenRequest request) {
        return client.asyncIamIdentityToken("password", request.username(), request.password(), "openid")
                .chain(tokenResponse -> client.asyncIamValidationRequest(request.username(), tokenResponse.accessToken())
                        .map(json -> new TokenResponse(
                                json.containsKey("accessToken") ? json.getString("accessToken") : json.getString("token"),
                                tokenResponse.refreshToken(),
                                tokenResponse.tokenType(),
                                tokenResponse.expiresIn(),
                                tokenResponse.expiration(),
                                tokenResponse.scope())))
                .subscribeAsCompletionStage();
    }

    public static final class QuarkusCP4DIAMRestClientBuilderFactory implements CP4DIAMRestClientBuilderFactory {
        @Override
        public Builder get() {
            return new QuarkusCP4DIAMRestClient.Builder();
        }
    }

    static final class Builder extends CP4DRestClient.Builder<QuarkusCP4DIAMRestClient, Builder> {
        @Override
        public QuarkusCP4DIAMRestClient build() {
            return new QuarkusCP4DIAMRestClient(this);
        }
    }
}
