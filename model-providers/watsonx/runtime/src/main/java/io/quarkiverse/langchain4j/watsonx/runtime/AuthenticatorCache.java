package io.quarkiverse.langchain4j.watsonx.runtime;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.ibm.watsonx.ai.core.auth.Authenticator;
import com.ibm.watsonx.ai.core.auth.ibmcloud.IBMCloudAuthenticator;

public class AuthenticatorCache {

    private static final Map<String, Authenticator> cache = new ConcurrentHashMap<>();

    private AuthenticatorCache() {
    }

    static Authenticator getOrCreateTokenGenerator(URI baseUrl, String apiKey) {
        return cache.computeIfAbsent(apiKey,
                new Function<String, Authenticator>() {
                    @Override
                    public Authenticator apply(String apiKey) {
                        return IBMCloudAuthenticator.builder()
                                .baseUrl(baseUrl)
                                .apiKey(apiKey)
                                .build();
                    }
                });
    }
}
