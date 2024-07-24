package io.quarkiverse.langchain4j.watsonx;

import java.net.URL;
import java.time.Duration;
import java.util.Date;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import io.quarkiverse.langchain4j.watsonx.bean.IdentityTokenRequest;
import io.quarkiverse.langchain4j.watsonx.bean.IdentityTokenResponse;
import io.quarkiverse.langchain4j.watsonx.client.IAMRestApi;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.smallrye.mutiny.Uni;

public class TokenGenerator {

    private final static Semaphore lock = new Semaphore(1);
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

    public Uni<String> generate() {

        try {

            lock.acquire();

            if (token != null) {

                Date expiration = new Date(TimeUnit.SECONDS.toMillis(token.expiration()));
                Date now = new Date();

                if (expiration.after(now)) {
                    lock.release();
                    return Uni.createFrom().item(token.accessToken());
                }
            }

            return client.generateBearer(new IdentityTokenRequest(grantType, apiKey))
                    .invoke(new Consumer<IdentityTokenResponse>() {
                        @Override
                        public void accept(IdentityTokenResponse result) {
                            token = result;
                        }
                    })
                    .map(IdentityTokenResponse::accessToken)
                    .onTermination().invoke(new Runnable() {
                        @Override
                        public void run() {
                            lock.release();
                        }
                    });

        } catch (Exception e) {
            lock.release();
            throw new RuntimeException(e);
        }
    }
}
