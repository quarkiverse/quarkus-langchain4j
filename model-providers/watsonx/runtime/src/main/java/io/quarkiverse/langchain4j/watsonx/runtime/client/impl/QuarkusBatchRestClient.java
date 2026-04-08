package io.quarkiverse.langchain4j.watsonx.runtime.client.impl;

import static io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils.retryOn;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.jboss.resteasy.reactive.client.api.LoggingScope;

import com.ibm.watsonx.ai.batch.BatchCancelRequest;
import com.ibm.watsonx.ai.batch.BatchCreateRequest;
import com.ibm.watsonx.ai.batch.BatchData;
import com.ibm.watsonx.ai.batch.BatchListRequest;
import com.ibm.watsonx.ai.batch.BatchListResponse;
import com.ibm.watsonx.ai.batch.BatchRestClient;
import com.ibm.watsonx.ai.batch.BatchRetrieveRequest;

import io.quarkiverse.langchain4j.watsonx.runtime.client.BatchRestApi;
import io.quarkiverse.langchain4j.watsonx.runtime.client.QuarkusRestClientConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.client.filter.BearerTokenHeaderFactory;
import io.quarkiverse.langchain4j.watsonx.runtime.client.logger.WatsonxClientLogger;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public final class QuarkusBatchRestClient extends BatchRestClient {

    private final BatchRestApi client;

    QuarkusBatchRestClient(Builder builder) {
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

            client = restClientBuilder.build(BatchRestApi.class);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public BatchData submit(BatchCreateRequest batchCreateRequest) {
        var requestId = UUID.randomUUID().toString();
        return retryOn(requestId, new Callable<BatchData>() {
            @Override
            public BatchData call() throws Exception {
                return client.submit(
                        requestId,
                        batchCreateRequest.transactionId(),
                        version,
                        batchCreateRequest.projectId(),
                        batchCreateRequest.spaceId(),
                        batchCreateRequest);
            }
        });
    }

    @Override
    public BatchListResponse list(BatchListRequest batchListRequest) {
        var requestId = UUID.randomUUID().toString();
        return retryOn(requestId, new Callable<BatchListResponse>() {
            @Override
            public BatchListResponse call() throws Exception {
                return client.list(
                        requestId,
                        batchListRequest.transactionId(),
                        version,
                        batchListRequest.projectId(),
                        batchListRequest.spaceId(),
                        batchListRequest.limit());
            }
        });
    }

    @Override
    public BatchData retrieve(BatchRetrieveRequest batchRetrieveRequest) {
        var requestId = UUID.randomUUID().toString();
        return retryOn(requestId, new Callable<BatchData>() {
            @Override
            public BatchData call() throws Exception {
                return client.retrieve(
                        requestId,
                        batchRetrieveRequest.transactionId(),
                        version,
                        batchRetrieveRequest.projectId(),
                        batchRetrieveRequest.spaceId(),
                        batchRetrieveRequest.batchId());
            }
        });
    }

    @Override
    public BatchData cancel(BatchCancelRequest batchCancelRequest) {
        var requestId = UUID.randomUUID().toString();
        return retryOn(requestId, new Callable<BatchData>() {
            @Override
            public BatchData call() throws Exception {
                return client.cancel(
                        requestId,
                        batchCancelRequest.transactionId(),
                        version,
                        batchCancelRequest.projectId(),
                        batchCancelRequest.spaceId(),
                        batchCancelRequest.batchId());
            }
        });
    }

    public static final class QuarkusBatchRestClientBuilderFactory implements BatchRestClientBuilderFactory {
        @Override
        public Builder get() {
            return new QuarkusBatchRestClient.Builder();
        }
    }

    static final class Builder extends BatchRestClient.Builder {
        @Override
        public BatchRestClient build() {
            return new QuarkusBatchRestClient(this);
        }
    }
}
