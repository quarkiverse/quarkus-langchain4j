package io.quarkiverse.langchain4j.watsonx.runtime.client.impl;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.ibm.watsonx.ai.core.auth.Authenticator;
import com.ibm.watsonx.ai.textprocessing.storage.cos.CosStorageRestClient;

/**
 * Shared cache of {@link CosStorageRestClient} instances.
 */
final class CosClients {

    private final ConcurrentHashMap<String, CosStorageRestClient> clients = new ConcurrentHashMap<>();
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

    CosStorageRestClient forBucket(String bucket) {
        return clients.computeIfAbsent(bucket, new Function<String, CosStorageRestClient>() {
            @Override
            public CosStorageRestClient apply(String bucketName) {
                return CosStorageRestClient.builder()
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
