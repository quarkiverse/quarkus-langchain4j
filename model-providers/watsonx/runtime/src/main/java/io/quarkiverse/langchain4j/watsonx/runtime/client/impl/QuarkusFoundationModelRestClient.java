package io.quarkiverse.langchain4j.watsonx.runtime.client.impl;

import static io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils.retryOn;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.jboss.resteasy.reactive.client.api.LoggingScope;

import com.ibm.watsonx.ai.foundationmodel.FoundationModel;
import com.ibm.watsonx.ai.foundationmodel.FoundationModelParameters;
import com.ibm.watsonx.ai.foundationmodel.FoundationModelResponse;
import com.ibm.watsonx.ai.foundationmodel.FoundationModelRestClient;
import com.ibm.watsonx.ai.foundationmodel.FoundationModelTask;

import io.quarkiverse.langchain4j.watsonx.runtime.client.FoundationModelRestApi;
import io.quarkiverse.langchain4j.watsonx.runtime.client.QuarkusRestClientConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.client.logger.WatsonxClientLogger;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public final class QuarkusFoundationModelRestClient extends FoundationModelRestClient {

    private final FoundationModelRestApi client;

    QuarkusFoundationModelRestClient(Builder builder) {
        super(builder);
        try {

            var logCurl = QuarkusRestClientConfig.isLogCurl();
            var restClientBuilder = QuarkusRestClientBuilder.newBuilder()
                    .baseUrl(URI.create(baseUrl).toURL())
                    .connectTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                    .readTimeout(timeout.toSeconds(), TimeUnit.SECONDS);

            if (logRequests || logResponses || logCurl) {
                restClientBuilder.loggingScope(LoggingScope.REQUEST_RESPONSE);
                restClientBuilder.clientLogger(new WatsonxClientLogger(logRequests, logResponses, logCurl));
            }

            client = restClientBuilder.build(FoundationModelRestApi.class);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public FoundationModelResponse<FoundationModel> getModels(
            Integer start, Integer limit,
            String transactionId, Boolean techPreview, String filters) {
        var requestId = UUID.randomUUID().toString();
        return retryOn(requestId, new Callable<FoundationModelResponse<FoundationModel>>() {
            @Override
            public FoundationModelResponse<FoundationModel> call() throws Exception {
                return client.getModels(start, limit, requestId, transactionId, techPreview, version, filters);
            }
        });
    }

    @Override
    public FoundationModelResponse<FoundationModelTask> getTasks(FoundationModelParameters parameters) {
        var requestId = UUID.randomUUID().toString();
        return retryOn(requestId, new Callable<FoundationModelResponse<FoundationModelTask>>() {
            @Override
            public FoundationModelResponse<FoundationModelTask> call() throws Exception {
                return client.getTasks(parameters.start(), parameters.limit(), requestId, parameters.transactionId(),
                        version);
            }
        });
    }

    public static final class QuarkusFoundationModelRestClientBuilderFactory
            implements FoundationModelRestClientBuilderFactory {
        @Override
        public Builder get() {
            return new QuarkusFoundationModelRestClient.Builder();
        }
    }

    static final class Builder extends FoundationModelRestClient.Builder {
        @Override
        public FoundationModelRestClient build() {
            return new QuarkusFoundationModelRestClient(this);
        }
    }
}
