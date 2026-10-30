package io.quarkiverse.langchain4j.watsonx.runtime.client.impl;

import static io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils.retryOn;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.jboss.resteasy.reactive.client.api.LoggingScope;

import com.ibm.watsonx.ai.core.auth.Authenticator;
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

            createSchemaClient = createClient(baseUrl, authenticator);
            cosClient = createClient(cosUrl, cosAuthenticator);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private CreateSchemaRestApi createClient(String url, Authenticator authenticator) throws MalformedURLException {

        var logCurl = QuarkusRestClientConfig.isLogCurl();
        var clientBuilder = QuarkusRestClientBuilder.newBuilder()
                .baseUrl(URI.create(url).toURL())
                .clientHeadersFactory(new BearerTokenHeaderFactory(authenticator))
                .connectTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                .readTimeout(timeout.toSeconds(), TimeUnit.SECONDS);

        if (logRequests || logResponses || logCurl) {
            clientBuilder.loggingScope(LoggingScope.REQUEST_RESPONSE);
            clientBuilder.clientLogger(new WatsonxClientLogger(logRequests, logResponses, logCurl));
        }

        return clientBuilder.build(CreateSchemaRestApi.class);
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
                cosClient.uploadFile(request.bucketName(), request.fileName(), request.requestTrackingId(), request.is());
                return true;
            }
        });
    }

    @Override
    public boolean deleteRequest(DeleteRequest request) {
        try {

            return retryOn(request.requestTrackingId(), new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    createSchemaClient.deleteRequest(
                            request.requestId(),
                            request.requestTrackingId(),
                            request.parameters().transactionId(),
                            request.parameters().projectId(),
                            request.parameters().spaceId(),
                            request.parameters().hardDelete().orElse(null),
                            version);
                    return true;
                }
            });

        } catch (WatsonxException e) {
            if (e.statusCode() == 404)
                return false;
            throw e;
        }
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
