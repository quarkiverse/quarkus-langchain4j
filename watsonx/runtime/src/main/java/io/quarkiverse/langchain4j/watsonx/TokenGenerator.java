package io.quarkiverse.langchain4j.watsonx;

import java.net.URL;
import java.time.Duration;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import io.quarkiverse.langchain4j.watsonx.bean.IdentityTokenRequest;
import io.quarkiverse.langchain4j.watsonx.bean.IdentityTokenResponse;
import io.quarkiverse.langchain4j.watsonx.client.IAMRestApi;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public class TokenGenerator {

    private static final ReentrantLock lock = new ReentrantLock();
    private final IAMRestApi client;
    private final String apiKey;
    private final String grantType;
    private IdentityTokenResponse token;

    public TokenGenerator(URL url, Duration timeout, String grantType, String apiKey) {

        this.client = QuarkusRestClientBuilder.newBuilder()
                .baseUrl(url)
                .connectTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                .readTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                .build(IAMRestApi.class);

        this.grantType = grantType;
        this.apiKey = apiKey;
    }

    public String generate() {

        try {

            lock.lock();

            if (token != null) {

                Date expiration = new Date(TimeUnit.SECONDS.toMillis(token.expiration()));
                Date now = new Date();

                if (expiration.after(now))
                    return token.accessToken();
            }

            token = client.generateBearer(new IdentityTokenRequest(grantType, apiKey));
            return token.accessToken();

        } finally {
            lock.unlock();
        }
    }

}
