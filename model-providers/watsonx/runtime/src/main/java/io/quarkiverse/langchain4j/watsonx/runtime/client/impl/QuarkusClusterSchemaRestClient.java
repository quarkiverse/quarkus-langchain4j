package io.quarkiverse.langchain4j.watsonx.runtime.client.impl;

import static io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils.retryOn;

import java.net.URI;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.jboss.resteasy.reactive.client.api.LoggingScope;

import com.ibm.watsonx.ai.core.exception.WatsonxException;
import com.ibm.watsonx.ai.textprocessing.schema.cluster.ClusterSchemaResponse;
import com.ibm.watsonx.ai.textprocessing.schema.cluster.ClusterSchemaRestClient;
import com.ibm.watsonx.ai.textprocessing.schema.cluster.DeleteRequest;
import com.ibm.watsonx.ai.textprocessing.schema.cluster.FetchDetailsRequest;
import com.ibm.watsonx.ai.textprocessing.schema.cluster.StartClusterSchemaRequest;

import io.quarkiverse.langchain4j.watsonx.runtime.client.ClusterSchemaRestApi;
import io.quarkiverse.langchain4j.watsonx.runtime.client.QuarkusRestClientConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.client.filter.BearerTokenHeaderFactory;
import io.quarkiverse.langchain4j.watsonx.runtime.client.logger.WatsonxClientLogger;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public final class QuarkusClusterSchemaRestClient extends ClusterSchemaRestClient {

    private final ClusterSchemaRestApi client;

    QuarkusClusterSchemaRestClient(Builder builder) {
        super(builder);
        try {

            var logCurl = QuarkusRestClientConfig.isLogCurl();
            var clientBuilder = QuarkusRestClientBuilder.newBuilder()
                    .baseUrl(URI.create(baseUrl).toURL())
                    .clientHeadersFactory(new BearerTokenHeaderFactory(authenticator))
                    .connectTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                    .readTimeout(timeout.toSeconds(), TimeUnit.SECONDS);

            if (logRequests || logResponses || logCurl) {
                clientBuilder.loggingScope(LoggingScope.REQUEST_RESPONSE);
                clientBuilder.clientLogger(new WatsonxClientLogger(logRequests, logResponses, logCurl));
            }

            client = clientBuilder.build(ClusterSchemaRestApi.class);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean deleteRequest(DeleteRequest request) {
        try {

            return retryOn(request.requestTrackingId(), new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    client.deleteRequest(
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
    public ClusterSchemaResponse fetchRequestDetails(FetchDetailsRequest request) {
        return retryOn(request.requestTrackingId(), new Callable<ClusterSchemaResponse>() {
            @Override
            public ClusterSchemaResponse call() throws Exception {
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
    public ClusterSchemaResponse startRequest(StartClusterSchemaRequest request) {
        return retryOn(request.requestTrackingId(), new Callable<ClusterSchemaResponse>() {
            @Override
            public ClusterSchemaResponse call() throws Exception {
                return client.startRequest(
                        request.requestTrackingId(),
                        request.transactionId(),
                        version,
                        request.clusterSchemaRequest());
            }
        });
    }

    public static final class QuarkusClusterSchemaRestClientBuilderFactory implements ClusterSchemaRestClientBuilderFactory {
        @Override
        public Builder get() {
            return new QuarkusClusterSchemaRestClient.Builder();
        }
    }

    static final class Builder extends ClusterSchemaRestClient.Builder {
        @Override
        public ClusterSchemaRestClient build() {
            return new QuarkusClusterSchemaRestClient(this);
        }
    }
}
