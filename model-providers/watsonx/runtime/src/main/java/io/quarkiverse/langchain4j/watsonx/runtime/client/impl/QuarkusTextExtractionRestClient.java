package io.quarkiverse.langchain4j.watsonx.runtime.client.impl;

import static io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils.retryOn;

import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.jboss.resteasy.reactive.client.api.LoggingScope;

import com.ibm.watsonx.ai.core.auth.Authenticator;
import com.ibm.watsonx.ai.textprocessing.DeleteFileRequest;
import com.ibm.watsonx.ai.textprocessing.ReadFileRequest;
import com.ibm.watsonx.ai.textprocessing.UploadRequest;
import com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionResponse;
import com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionRestClient;

import io.quarkiverse.langchain4j.watsonx.runtime.client.QuarkusRestClientConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.client.TextExtractionRestApi;
import io.quarkiverse.langchain4j.watsonx.runtime.client.filter.BearerTokenHeaderFactory;
import io.quarkiverse.langchain4j.watsonx.runtime.client.logger.WatsonxClientLogger;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public final class QuarkusTextExtractionRestClient extends TextExtractionRestClient {

    private final TextExtractionRestApi textExtractionClient;
    private final CosClients cosClients;

    QuarkusTextExtractionRestClient(Builder builder) {
        super(builder);
        try {

            textExtractionClient = createClient(baseUrl, authenticator);
            cosClients = new CosClients(cosUrl, authenticator, cosAuthenticator, timeout, logRequests, logResponses);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private TextExtractionRestApi createClient(String url, Authenticator authenticator) throws MalformedURLException {

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

        return clientBuilder.build(TextExtractionRestApi.class);
    }

    @Override
    public boolean deleteFile(DeleteFileRequest request) throws FileNotFoundException {
        try {
            return cosClients.forBucket(request.bucketName()).deleteFileAsync(request).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException re)
                throw re;
            throw new RuntimeException(e.getCause());
        }
    }

    @Override
    public CompletableFuture<Boolean> deleteFileAsync(DeleteFileRequest request) {
        return cosClients.forBucket(request.bucketName()).deleteFileAsync(request);
    }

    @Override
    public String readFile(ReadFileRequest request) throws FileNotFoundException {
        return cosClients.forBucket(request.bucketName()).readFile(request.requestTrackingId(), request.fileName());
    }

    @Override
    public boolean upload(UploadRequest request) {
        return cosClients.forBucket(request.bucketName())
                .upload(request.requestTrackingId(), request.is(), request.fileName());
    }

    @Override
    public boolean deleteExtraction(DeleteExtractionRequest request) {
        return retryOn(request.requestTrackingId(), new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                textExtractionClient.deleteExtraction(
                        request.extractionId(),
                        request.requestTrackingId(),
                        request.parameters().transactionId(),
                        request.parameters().projectId(),
                        request.parameters().spaceId(),
                        request.parameters().hardDelete().orElse(null),
                        version);
                return true;
            }
        });
    }

    @Override
    public TextExtractionResponse fetchExtractionDetails(FetchExtractionDetailsRequest request) {
        return retryOn(request.requestTrackingId(), new Callable<TextExtractionResponse>() {
            @Override
            public TextExtractionResponse call() throws Exception {
                return textExtractionClient.fetchExtractionDetails(
                        request.extractionId(),
                        request.requestTrackingId(),
                        request.parameters().transactionId(),
                        request.parameters().projectId(),
                        request.parameters().spaceId(),
                        version);
            }
        });
    }

    @Override
    public TextExtractionResponse startExtraction(StartExtractionRequest request) {
        return retryOn(request.requestTrackingId(), new Callable<TextExtractionResponse>() {
            @Override
            public TextExtractionResponse call() throws Exception {
                return textExtractionClient.startExtraction(
                        request.requestTrackingId(),
                        request.transactionId(),
                        version,
                        request.textExtractionRequest());
            }
        });
    }

    public static final class QuarkusTextExtractionRestClientBuilderFactory implements TextExtractionRestClientBuilderFactory {
        @Override
        public Builder get() {
            return new QuarkusTextExtractionRestClient.Builder();
        }
    }

    static final class Builder extends TextExtractionRestClient.Builder {
        @Override
        public TextExtractionRestClient build() {
            return new QuarkusTextExtractionRestClient(this);
        }
    }
}
