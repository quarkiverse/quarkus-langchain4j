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
import com.ibm.watsonx.ai.textprocessing.textclassification.TextClassificationResponse;
import com.ibm.watsonx.ai.textprocessing.textclassification.TextClassificationRestClient;

import io.quarkiverse.langchain4j.watsonx.runtime.client.QuarkusRestClientConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.client.TextClassificationRestApi;
import io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils;
import io.quarkiverse.langchain4j.watsonx.runtime.client.filter.BearerTokenHeaderFactory;
import io.quarkiverse.langchain4j.watsonx.runtime.client.logger.WatsonxClientLogger;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public final class QuarkusTextClassificationRestClient extends TextClassificationRestClient {

    private final TextClassificationRestApi textClassificationClient;
    private final TextClassificationRestApi cosClient;

    QuarkusTextClassificationRestClient(Builder builder) {
        super(builder);
        try {

            textClassificationClient = createClient(baseUrl, authenticator);
            cosClient = createClient(cosUrl, cosAuthenticator);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private TextClassificationRestApi createClient(String url, Authenticator authenticator) throws MalformedURLException {

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

        return clientBuilder.build(TextClassificationRestApi.class);
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
    public boolean upload(UploadRequest request) {
        return retryOn(request.requestTrackingId(), new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                cosClient.upload(request.bucketName(), request.fileName(), request.requestTrackingId(), request.is());
                return true;
            }
        });
    }

    @Override
    public boolean deleteClassification(DeleteClassificationRequest request) {
        return retryOn(request.requestTrackingId(), new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                try {
                    textClassificationClient.deleteClassification(
                            request.classificationId(),
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
    public TextClassificationResponse fetchClassificationDetails(FetchClassificationDetailsRequest request) {
        return retryOn(request.requestTrackingId(), new Callable<TextClassificationResponse>() {
            @Override
            public TextClassificationResponse call() throws Exception {
                return textClassificationClient.fetchClassificationDetails(
                        request.classificationId(),
                        request.requestTrackingId(),
                        request.parameters().transactionId(),
                        request.parameters().projectId(),
                        request.parameters().spaceId(),
                        version);
            }
        });
    }

    @Override
    public TextClassificationResponse startClassification(StartClassificationRequest request) {
        return retryOn(request.requestTrackingId(), new Callable<TextClassificationResponse>() {
            @Override
            public TextClassificationResponse call() throws Exception {
                return textClassificationClient.startClassification(
                        request.requestTrackingId(),
                        request.transactionId(),
                        version,
                        request.textClassificationRequest());
            }
        });
    }

    public static final class QuarkusTextClassificationRestClientBuilderFactory
            implements TextClassificationRestClientBuilderFactory {
        @Override
        public Builder get() {
            return new QuarkusTextClassificationRestClient.Builder();
        }
    }

    static final class Builder extends TextClassificationRestClient.Builder {
        @Override
        public TextClassificationRestClient build() {
            return new QuarkusTextClassificationRestClient(this);
        }
    }
}
