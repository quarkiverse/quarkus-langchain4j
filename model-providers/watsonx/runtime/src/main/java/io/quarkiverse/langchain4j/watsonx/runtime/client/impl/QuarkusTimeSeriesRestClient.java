package io.quarkiverse.langchain4j.watsonx.runtime.client.impl;

import static io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils.retryOn;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.jboss.resteasy.reactive.client.api.LoggingScope;

import com.ibm.watsonx.ai.timeseries.ForecastRequest;
import com.ibm.watsonx.ai.timeseries.ForecastResponse;
import com.ibm.watsonx.ai.timeseries.TimeSeriesRestClient;

import io.quarkiverse.langchain4j.watsonx.runtime.client.QuarkusRestClientConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.client.TimeSeriesRestApi;
import io.quarkiverse.langchain4j.watsonx.runtime.client.filter.BearerTokenHeaderFactory;
import io.quarkiverse.langchain4j.watsonx.runtime.client.logger.WatsonxClientLogger;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public final class QuarkusTimeSeriesRestClient extends TimeSeriesRestClient {

    private final TimeSeriesRestApi client;

    QuarkusTimeSeriesRestClient(Builder builder) {
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

            client = restClientBuilder.build(TimeSeriesRestApi.class);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ForecastResponse forecast(String transactionId, ForecastRequest request) {
        var requestId = UUID.randomUUID().toString();
        return retryOn(requestId, new Callable<ForecastResponse>() {
            @Override
            public ForecastResponse call() throws Exception {
                return client.forecast(requestId, transactionId, version, request);
            }
        });
    }

    public static final class QuarkusTimeSeriesRestClientBuilderFactory implements TimeSeriesRestClientBuilderFactory {
        @Override
        public Builder get() {
            return new QuarkusTimeSeriesRestClient.Builder();
        }
    }

    static final class Builder extends TimeSeriesRestClient.Builder {
        @Override
        public TimeSeriesRestClient build() {
            return new QuarkusTimeSeriesRestClient(this);
        }
    }
}
