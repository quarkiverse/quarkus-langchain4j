package io.quarkiverse.langchain4j.watsonx.runtime.client.impl;

import static io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils.retryOn;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.jboss.resteasy.reactive.client.api.LoggingScope;

import com.ibm.watsonx.ai.detection.DetectionResponse;
import com.ibm.watsonx.ai.detection.DetectionRestClient;
import com.ibm.watsonx.ai.detection.DetectionTextResponse;
import com.ibm.watsonx.ai.detection.TextDetectionContentDetectors;

import io.quarkiverse.langchain4j.watsonx.runtime.client.DetectionRestApi;
import io.quarkiverse.langchain4j.watsonx.runtime.client.QuarkusRestClientConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.client.filter.BearerTokenHeaderFactory;
import io.quarkiverse.langchain4j.watsonx.runtime.client.logger.WatsonxClientLogger;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public final class QuarkusDetectionRestClient extends DetectionRestClient {

    private final DetectionRestApi client;

    QuarkusDetectionRestClient(Builder builder) {
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

            client = restClientBuilder.build(DetectionRestApi.class);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public DetectionResponse<DetectionTextResponse> detect(String transactionId, TextDetectionContentDetectors request) {
        var requestId = UUID.randomUUID().toString();
        return retryOn(requestId, new Callable<DetectionResponse<DetectionTextResponse>>() {
            @Override
            public DetectionResponse<DetectionTextResponse> call() throws Exception {
                return client.detect(requestId, transactionId, version, request);
            }
        });
    }

    public static final class QuarkusDetectionRestClientBuilderFactory implements DetectionRestClientBuilderFactory {
        @Override
        public Builder get() {
            return new QuarkusDetectionRestClient.Builder();
        }
    }

    static final class Builder extends DetectionRestClient.Builder {
        @Override
        public DetectionRestClient build() {
            return new QuarkusDetectionRestClient(this);
        }
    }
}
