package io.quarkiverse.langchain4j.watsonx.runtime.client.impl;

import static io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils.retryOn;

import java.net.URI;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.jboss.resteasy.reactive.client.api.LoggingScope;

import com.ibm.watsonx.ai.core.exception.WatsonxException;
import com.ibm.watsonx.ai.textprocessing.schema.merge.DeleteRequest;
import com.ibm.watsonx.ai.textprocessing.schema.merge.MergeFetchDetailsRequest;
import com.ibm.watsonx.ai.textprocessing.schema.merge.MergeSchemaResponse;
import com.ibm.watsonx.ai.textprocessing.schema.merge.MergeSchemaRestClient;
import com.ibm.watsonx.ai.textprocessing.schema.merge.StartMergeSchemaRequest;

import io.quarkiverse.langchain4j.watsonx.runtime.client.MergeSchemaRestApi;
import io.quarkiverse.langchain4j.watsonx.runtime.client.QuarkusRestClientConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.client.filter.BearerTokenHeaderFactory;
import io.quarkiverse.langchain4j.watsonx.runtime.client.logger.WatsonxClientLogger;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public final class QuarkusMergeSchemaRestClient extends MergeSchemaRestClient {

    private final MergeSchemaRestApi client;

    QuarkusMergeSchemaRestClient(Builder builder) {
        super(builder);
        try {

            var logCurl = QuarkusRestClientConfig.isLogCurl();
            var mergeSchemaClientBuilder = QuarkusRestClientBuilder.newBuilder()
                    .baseUrl(URI.create(baseUrl).toURL())
                    .clientHeadersFactory(new BearerTokenHeaderFactory(authenticator))
                    .connectTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                    .readTimeout(timeout.toSeconds(), TimeUnit.SECONDS);

            if (logRequests || logResponses || logCurl) {
                mergeSchemaClientBuilder.loggingScope(LoggingScope.REQUEST_RESPONSE);
                mergeSchemaClientBuilder.clientLogger(new WatsonxClientLogger(logRequests, logResponses, logCurl));
            }

            client = mergeSchemaClientBuilder.build(MergeSchemaRestApi.class);

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
    public MergeSchemaResponse fetchRequestDetails(MergeFetchDetailsRequest request) {
        return retryOn(request.requestTrackingId(), new Callable<MergeSchemaResponse>() {
            @Override
            public MergeSchemaResponse call() throws Exception {
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
    public MergeSchemaResponse startRequest(StartMergeSchemaRequest request) {
        return retryOn(request.requestTrackingId(), new Callable<MergeSchemaResponse>() {
            @Override
            public MergeSchemaResponse call() throws Exception {
                return client.startRequest(
                        request.requestTrackingId(),
                        request.transactionId(),
                        version,
                        request.mergeSchemaRequest());
            }
        });
    }

    public static final class QuarkusMergeSchemaRestClientBuilderFactory implements MergeSchemaRestClientBuilderFactory {
        @Override
        public Builder get() {
            return new QuarkusMergeSchemaRestClient.Builder();
        }
    }

    static final class Builder extends MergeSchemaRestClient.Builder {
        @Override
        public MergeSchemaRestClient build() {
            return new QuarkusMergeSchemaRestClient(this);
        }
    }
}
