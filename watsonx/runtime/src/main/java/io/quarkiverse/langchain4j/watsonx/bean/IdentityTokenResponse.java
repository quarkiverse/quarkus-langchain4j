package io.quarkiverse.langchain4j.watsonx.bean;

public record IdentityTokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        long expiration,
        String scope) {
}
