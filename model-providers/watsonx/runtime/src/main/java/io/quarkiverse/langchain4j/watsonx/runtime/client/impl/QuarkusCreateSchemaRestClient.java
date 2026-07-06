package io.quarkiverse.langchain4j.watsonx.runtime.client.impl;

import static io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils.retryOn;

import java.net.URI;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.jboss.resteasy.reactive.client.api.LoggingScope;

import com.ibm.watsonx.ai.core.exception.WatsonxException;
import com.ibm.watsonx.ai.textprocessing.DeleteFileRequest;
import com.ibm.watsonx.ai.textprocessing.UploadRequest;
import com.ibm.watsonx.ai.textprocessing.schema.create.CreateSchemaResponse;
import com.ibm.watsonx.ai.textprocessing.schema.create.CreateSchemaRestClient;
import com.ibm.watsonx.ai.textprocessing.schema.create.DeleteRequest;
import com.ibm.watsonx.ai.textprocessing.schema.create.FetchDetailsRequest;
import com.ibm.watsonx.ai.textprocessing.schema.create.StartCreateSchemaRequest;

import io.quarkiverse.langchain4j.watsonx.runtime.client.CreateSchemaRestApi;
import io.quarkiverse.langchain4j.watsonx.runtime.client.QuarkusRestClientConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils;
import io.quarkiverse.langchain4j.watsonx.runtime.client.filter.BearerTokenHeaderFactory;
import io.quarkiverse.langchain4j.watsonx.runtime.client.logger.WatsonxClientLogger;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public final class QuarkusCreateSchemaRestClient extends CreateSchemaRestClient {

    private final CreateSchemaRestApi createSchemaClient;
    private final CreateSchemaRestApi cosClient;

    QuarkusCreateSchemaRestClient(Builder builder) {
        super(builder);
        try {

            var logCurl = QuarkusRestClientConfig.isLogCurl();

            var createSchemaClientBuilder = QuarkusRestClientBuilder.newBuilder()
                    .clientHeadersFactory(new BearerTokenHeaderFactory(authenticator))
                    .connectTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                    .readTimeout(timeout.toSeconds(), TimeUnit.SECONDS);

            // COS may live in a different environment and use a dedicated authenticator
            // (defaults to the main authenticator when none is provided).
            var cosClientBuilder = QuarkusRestClientBuilder.newBuilder()
                    .clientHeadersFactory(new BearerTokenHeaderFactory(cosAuthenticator))
                    .connectTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                    .readTimeout(timeout.toSeconds(), TimeUnit.SECONDS);

            if (logRequests || logResponses || logCurl) {
                createSchemaClientBuilder.loggingScope(LoggingScope.REQUEST_RESPONSE);
                createSchemaClientBuilder.clientLogger(new WatsonxClientLogger(logRequests, logResponses, logCurl));
                cosClientBuilder.loggingScope(LoggingScope.REQUEST_RESPONSE);
                cosClientBuilder.clientLogger(new WatsonxClientLogger(logRequests, logResponses, logCurl));
            }

            createSchemaClient = createSchemaClientBuilder.baseUrl(URI.create(baseUrl).toURL())
                    .build(CreateSchemaRestApi.class);

            cosClient = cosClientBuilder.baseUrl(URI.create(cosUrl).toURL())
                    .build(CreateSchemaRestApi.class);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean deleteFile(DeleteFileRequest request) {
        return retryOn(request.requestTrackingId(), new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                cosClient.deleteFile(request.bucketName(), request.fileName(), request.requestTrackingId());
                return true;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> deleteFileAsync(DeleteFileRequest request) {
        return cosClient.deleteFileAsync(request.bucketName(), request.fileName(), request.requestTrackingId())
                .map(v -> true)
                .onFailure(WatsonxRestClientUtils::shouldRetry).retry().atMost(10)
                .subscribe().asCompletionStage();
    }

    @Override
    public boolean uploadFile(UploadRequest request) {
        return retryOn(request.requestTrackingId(), new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                cosClient.upload(request.bucketName(), request.fileName(), request.requestTrackingId(), request.is());
                return true;
            }
        });
    }

    @Override
    public boolean deleteRequest(DeleteRequest request) {
        return retryOn(request.requestTrackingId(), new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                try {
                    createSchemaClient.deleteRequest(
                            request.requestId(),
                            request.requestTrackingId(),
                            request.parameters().transactionId(),
                            request.parameters().projectId(),
                            request.parameters().spaceId(),
                            request.parameters().hardDelete().orElse(null),
                            version);
                    return true;
                } catch (WatsonxException e) {
                    if (e.statusCode() == 404)
                        return false;
                    throw e;
                }
            }
        });
    }

    @Override
    public CreateSchemaResponse fetchRequestDetails(FetchDetailsRequest request) {
        return retryOn(request.requestTrackingId(), new Callable<CreateSchemaResponse>() {
            @Override
            public CreateSchemaResponse call() throws Exception {
                return createSchemaClient.fetchRequestDetails(
                        request.requestId(),
                        request.requestTrackingId(),
                        request.parameters().transactionId(),
                        request.parameters().projectId(),
                        request.parameters().spaceId(),
                        version);
            }
        });
    }

    @Override
    public CreateSchemaResponse startRequest(StartCreateSchemaRequest request) {
        return retryOn(request.requestTrackingId(), new Callable<CreateSchemaResponse>() {
            @Override
            public CreateSchemaResponse call() throws Exception {
                return createSchemaClient.startRequest(
                        request.requestTrackingId(),
                        request.transactionId(),
                        version,
                        request.createSchemaRequest());
            }
        });
    }

    public static final class QuarkusCreateSchemaRestClientBuilderFactory implements CreateSchemaRestClientBuilderFactory {
        @Override
        public Builder get() {
            return new QuarkusCreateSchemaRestClient.Builder();
        }
    }

    static final class Builder extends CreateSchemaRestClient.Builder {
        @Override
        public CreateSchemaRestClient build() {
            return new QuarkusCreateSchemaRestClient(this);
        }
    }
}
