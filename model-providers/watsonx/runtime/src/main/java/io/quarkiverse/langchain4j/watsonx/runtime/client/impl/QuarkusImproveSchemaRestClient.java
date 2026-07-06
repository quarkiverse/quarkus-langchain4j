package io.quarkiverse.langchain4j.watsonx.runtime.client.impl;

import static io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils.retryOn;

import java.net.URI;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.jboss.resteasy.reactive.client.api.LoggingScope;

import com.ibm.watsonx.ai.core.exception.WatsonxException;
import com.ibm.watsonx.ai.textprocessing.schema.improve.DeleteRequest;
import com.ibm.watsonx.ai.textprocessing.schema.improve.ImproveFetchDetailsRequest;
import com.ibm.watsonx.ai.textprocessing.schema.improve.ImproveSchemaResponse;
import com.ibm.watsonx.ai.textprocessing.schema.improve.ImproveSchemaRestClient;
import com.ibm.watsonx.ai.textprocessing.schema.improve.StartImproveSchemaRequest;

import io.quarkiverse.langchain4j.watsonx.runtime.client.ImproveSchemaRestApi;
import io.quarkiverse.langchain4j.watsonx.runtime.client.QuarkusRestClientConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.client.filter.BearerTokenHeaderFactory;
import io.quarkiverse.langchain4j.watsonx.runtime.client.logger.WatsonxClientLogger;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public final class QuarkusImproveSchemaRestClient extends ImproveSchemaRestClient {

    private final ImproveSchemaRestApi client;

    QuarkusImproveSchemaRestClient(Builder builder) {
        super(builder);
        try {

            var logCurl = QuarkusRestClientConfig.isLogCurl();
            var improveSchemaClientBuilder = QuarkusRestClientBuilder.newBuilder()
                    .baseUrl(URI.create(baseUrl).toURL())
                    .clientHeadersFactory(new BearerTokenHeaderFactory(authenticator))
                    .connectTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                    .readTimeout(timeout.toSeconds(), TimeUnit.SECONDS);

            if (logRequests || logResponses || logCurl) {
                improveSchemaClientBuilder.loggingScope(LoggingScope.REQUEST_RESPONSE);
                improveSchemaClientBuilder.clientLogger(new WatsonxClientLogger(logRequests, logResponses, logCurl));
            }

            client = improveSchemaClientBuilder.build(ImproveSchemaRestApi.class);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean deleteRequest(DeleteRequest request) {
        return retryOn(request.requestTrackingId(), new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                try {
                    client.deleteRequest(
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
    public ImproveSchemaResponse fetchRequestDetails(ImproveFetchDetailsRequest request) {
        return retryOn(request.requestTrackingId(), new Callable<ImproveSchemaResponse>() {
            @Override
            public ImproveSchemaResponse call() throws Exception {
                return client.fetchRequestDetails(
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
    public ImproveSchemaResponse startRequest(StartImproveSchemaRequest request) {
        return retryOn(request.requestTrackingId(), new Callable<ImproveSchemaResponse>() {
            @Override
            public ImproveSchemaResponse call() throws Exception {
                return client.startRequest(
                        request.requestTrackingId(),
                        request.transactionId(),
                        version,
                        request.improveSchemaRequest());
            }
        });
    }

    public static final class QuarkusImproveSchemaRestClientBuilderFactory implements ImproveSchemaRestClientBuilderFactory {
        @Override
        public Builder get() {
            return new QuarkusImproveSchemaRestClient.Builder();
        }
    }

    static final class Builder extends ImproveSchemaRestClient.Builder {
        @Override
        public ImproveSchemaRestClient build() {
            return new QuarkusImproveSchemaRestClient(this);
        }
    }
}
