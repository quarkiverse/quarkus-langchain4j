package io.quarkiverse.langchain4j.watsonx.runtime.client.impl;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.ibm.watsonx.ai.core.auth.Authenticator;

/**
 * Shared cache of {@link QuarkusCosStorageRestClient} instances.
 */
final class CosClients {

    private final ConcurrentHashMap<String, QuarkusCosStorageRestClient> clients = new ConcurrentHashMap<>();
    private final String cosUrl;
    private final Authenticator authenticator;
    private final Authenticator cosAuthenticator;
    private final Duration timeout;
    private final boolean logRequests;
    private final boolean logResponses;

    CosClients(String cosUrl, Authenticator authenticator, Authenticator cosAuthenticator, Duration timeout,
            boolean logRequests, boolean logResponses) {
        this.cosUrl = cosUrl;
        this.authenticator = authenticator;
        this.cosAuthenticator = cosAuthenticator;
        this.timeout = timeout;
        this.logRequests = logRequests;
        this.logResponses = logResponses;
    }

    QuarkusCosStorageRestClient forBucket(String bucket) {
        return clients.computeIfAbsent(bucket, new Function<String, QuarkusCosStorageRestClient>() {
            @Override
            public QuarkusCosStorageRestClient apply(String bucketName) {
                return new QuarkusCosStorageRestClient.Builder()
                        .cosUrl(cosUrl)
                        .bucket(bucketName)
                        .authenticator(authenticator)
                        .cosAuthenticator(cosAuthenticator)
                        .timeout(timeout)
                        .logRequests(logRequests)
                        .logResponses(logResponses)
                        .build();
            }
        });
    }
}
