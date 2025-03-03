package io.quarkiverse.langchain4j.watsonx.runtime;

import java.net.URL;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class TokenGenerationCache {

    private static final Map<String, TokenGenerator> cache = new ConcurrentHashMap<>();

    public static Optional<TokenGenerator> get(String apiKey) {
        return Optional.ofNullable(cache.get(apiKey));
    }

    public static TokenGenerator getOrCreateTokenGenerator(String apiKey, URL iamBaseUrl, String grantType,
            Duration timeout) {
        return cache.computeIfAbsent(apiKey,
                new Function<String, TokenGenerator>() {
                    @Override
                    public TokenGenerator apply(String apiKey) {
                        return new TokenGenerator(iamBaseUrl, timeout, grantType, apiKey);
                    }
                });
    }
}