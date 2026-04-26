package io.quarkiverse.langchain4j.watsonx.runtime.client.impl;

import static io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils.retryOn;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.jboss.resteasy.reactive.client.api.LoggingScope;

import com.ibm.watsonx.ai.file.FileData;
import com.ibm.watsonx.ai.file.FileDeleteRequest;
import com.ibm.watsonx.ai.file.FileDeleteResponse;
import com.ibm.watsonx.ai.file.FileListRequest;
import com.ibm.watsonx.ai.file.FileListResponse;
import com.ibm.watsonx.ai.file.FileRestClient;
import com.ibm.watsonx.ai.file.FileRetrieveRequest;
import com.ibm.watsonx.ai.file.FileUploadRequest;

import io.quarkiverse.langchain4j.watsonx.runtime.client.FileRestApi;
import io.quarkiverse.langchain4j.watsonx.runtime.client.QuarkusRestClientConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils;
import io.quarkiverse.langchain4j.watsonx.runtime.client.filter.BearerTokenHeaderFactory;
import io.quarkiverse.langchain4j.watsonx.runtime.client.logger.WatsonxClientLogger;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public final class QuarkusFileRestClient extends FileRestClient {

    private final FileRestApi client;

    QuarkusFileRestClient(Builder builder) {
        super(builder);
        try {

            var logCurl = QuarkusRestClientConfig.isLogCurl();
            var restClientBuilder = QuarkusRestClientBuilder.newBuilder()
                    .baseUrl(URI.create(baseUrl).toURL())
                    .clientHeadersFactory(new BearerTokenHeaderFactory(authenticator))
                    .connectTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                    .readTimeout(timeout.toSeconds(), TimeUnit.SECONDS);

            if (logRequests || logResponses || logCurl) {
                restClientBuilder.loggingScope(LoggingScope.REQUEST_RESPONSE);
                restClientBuilder.clientLogger(new WatsonxClientLogger(logRequests, logResponses, logCurl));
            }

            client = restClientBuilder.build(FileRestApi.class);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public FileData upload(FileUploadRequest fileUploadRequest) {
        var requestId = UUID.randomUUID().toString();
        return retryOn(requestId, new Callable<FileData>() {
            @Override
            public FileData call() throws Exception {
                return client.upload(
                        requestId,
                        fileUploadRequest.transactionId(),
                        version,
                        fileUploadRequest.projectId(),
                        fileUploadRequest.spaceId(),
                        fileUploadRequest.inputStream(),
                        fileUploadRequest.purpose().value());
            }
        });
    }

    @Override
    public FileListResponse list(FileListRequest fileListRequest) {
        var requestId = UUID.randomUUID().toString();
        return retryOn(requestId, new Callable<FileListResponse>() {
            @Override
            public FileListResponse call() throws Exception {
                return client.list(
                        requestId,
                        fileListRequest.transactionId(),
                        version,
                        fileListRequest.projectId(),
                        fileListRequest.spaceId(),
                        fileListRequest.after(), fileListRequest.limit(), fileListRequest.order(), fileListRequest.purpose());
            }
        });
    }

    @Override
    public String retrieve(FileRetrieveRequest fileRetrieveRequest) {
        var requestId = UUID.randomUUID().toString();
        return retryOn(requestId, new Callable<String>() {
            @Override
            public String call() throws Exception {
                return client.retrieve(requestId,
                        fileRetrieveRequest.transactionId(),
                        version,
                        fileRetrieveRequest.projectId(),
                        fileRetrieveRequest.spaceId(),
                        fileRetrieveRequest.fileId());
            }
        });
    }

    @Override
    public FileDeleteResponse delete(FileDeleteRequest fileDeleteRequest) {
        var requestId = UUID.randomUUID().toString();
        return retryOn(requestId, new Callable<FileDeleteResponse>() {
            @Override
            public FileDeleteResponse call() throws Exception {
                return client.delete(requestId,
                        fileDeleteRequest.transactionId(),
                        version,
                        fileDeleteRequest.projectId(),
                        fileDeleteRequest.spaceId(),
                        fileDeleteRequest.fileId());
            }
        });
    }

    @Override
    public CompletableFuture<FileDeleteResponse> deleteAsync(FileDeleteRequest fileDeleteRequest) {
        var requestId = UUID.randomUUID().toString();
        return client.deleteAsync(requestId, fileDeleteRequest.transactionId(), version,
                fileDeleteRequest.projectId(), fileDeleteRequest.spaceId(), fileDeleteRequest.fileId())
                .onFailure(WatsonxRestClientUtils::shouldRetry).retry().atMost(10)
                .subscribeAsCompletionStage();
    }

    public static final class QuarkusFileRestClientBuilderFactory implements FileRestClientBuilderFactory {
        @Override
        public Builder get() {
            return new QuarkusFileRestClient.Builder();
        }
    }

    static final class Builder extends FileRestClient.Builder {
        @Override
        public FileRestClient build() {
            return new QuarkusFileRestClient(this);
        }
    }
}
