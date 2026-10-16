package io.quarkiverse.langchain4j.watsonx.runtime.client.impl;

import static io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils.retryOn;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.jboss.resteasy.reactive.client.api.LoggingScope;

import com.ibm.watsonx.ai.textprocessing.DeleteFileRequest;
import com.ibm.watsonx.ai.textprocessing.storage.cos.CosStorageRestClient;

import io.quarkiverse.langchain4j.watsonx.runtime.client.CosStorageRestApi;
import io.quarkiverse.langchain4j.watsonx.runtime.client.QuarkusRestClientConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils;
import io.quarkiverse.langchain4j.watsonx.runtime.client.filter.BearerTokenHeaderFactory;
import io.quarkiverse.langchain4j.watsonx.runtime.client.logger.WatsonxClientLogger;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.smallrye.mutiny.Uni;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;

public final class QuarkusCosStorageRestClient extends CosStorageRestClient {

    private final CosStorageRestApi client;

    QuarkusCosStorageRestClient(Builder builder) {
        super(builder);
        try {

            var logCurl = QuarkusRestClientConfig.isLogCurl(authenticator);
            var restClientBuilder = QuarkusRestClientBuilder.newBuilder()
                    .baseUrl(URI.create(cosUrl).toURL())
                    .clientHeadersFactory(new BearerTokenHeaderFactory(cosAuthenticator))
                    .connectTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                    .readTimeout(timeout.toSeconds(), TimeUnit.SECONDS);

            if (logRequests || logResponses || logCurl) {
                var clientLogger = new WatsonxClientLogger(logRequests, logResponses, logCurl);
                restClientBuilder.loggingScope(LoggingScope.REQUEST_RESPONSE);
                restClientBuilder.clientLogger(clientLogger);
                // Quarkus REST skips request logging for InputStream uploads. Log after sending so headers are available.
                restClientBuilder.httpClientRequestCustomizer(new Consumer<HttpClientRequest>() {
                    @Override
                    public void accept(HttpClientRequest request) {
                        if ((logRequests || logCurl) && HttpMethod.PUT.equals(request.getMethod())) {
                            request.response().onComplete(new Handler<AsyncResult<HttpClientResponse>>() {
                                @Override
                                public void handle(AsyncResult<HttpClientResponse> response) {
                                    clientLogger.logRequest(request, null, true);
                                }
                            });
                        }
                    }
                });
            }

            client = restClientBuilder.build(CosStorageRestApi.class);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean upload(String requestTrackingId, InputStream is, String fileName) {
        return retryOn(requestTrackingId, new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                client.upload(bucket, fileName, requestTrackingId, is);
                return true;
            }
        });
    }

    @Override
    public boolean upload(String requestTrackingId, File file) {
        try (var is = new FileInputStream(file)) {
            return upload(requestTrackingId, is, file.getName());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String uploadAndGetId(String requestTrackingId, InputStream is, String fileName) {
        // an object of a COS bucket has no id other than its name
        upload(requestTrackingId, is, fileName);
        return null;
    }

    @Override
    public String readFile(String requestTrackingId, String fileName) {
        return retryOn(requestTrackingId, new Callable<String>() {
            @Override
            public String call() throws Exception {
                return client.readFile(bucket, fileName, requestTrackingId);
            }
        });
    }

    boolean deleteFile(DeleteFileRequest request) {
        return retryOn(request.requestTrackingId(), new Callable<Boolean>() {
            @Override
            public Boolean call() {
                client.deleteFile(bucket, request.fileName(), request.requestTrackingId());
                return true;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> deleteFileAsync(DeleteFileRequest request) {
        // Recreate the invocation on retry so authentication headers are refreshed.
        return Uni.createFrom().deferred(new Supplier<Uni<? extends Void>>() {
            @Override
            public Uni<? extends Void> get() {
                return client.deleteFileAsync(bucket, request.fileName(), request.requestTrackingId());
            }
        })
                .replaceWith(true)
                .onFailure(new Predicate<Throwable>() {
                    @Override
                    public boolean test(Throwable throwable) {
                        return WatsonxRestClientUtils.shouldRetry(throwable);
                    }
                }).retry().withBackOff(Duration.ofMillis(20)).withJitter(0).atMost(10)
                .subscribe().asCompletionStage();
    }

    public static final class QuarkusCosStorageRestClientBuilderFactory implements CosStorageRestClientBuilderFactory {
        @Override
        public Builder get() {
            return new QuarkusCosStorageRestClient.Builder();
        }
    }

    static final class Builder extends CosStorageRestClient.Builder<Builder> {
        @Override
        public QuarkusCosStorageRestClient build() {
            return new QuarkusCosStorageRestClient(this);
        }
    }
}
