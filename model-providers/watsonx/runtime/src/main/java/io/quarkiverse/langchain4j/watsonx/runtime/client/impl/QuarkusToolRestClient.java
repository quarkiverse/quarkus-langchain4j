package io.quarkiverse.langchain4j.watsonx.runtime.client.impl;

import static io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils.retryOn;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.jboss.resteasy.reactive.client.api.LoggingScope;

import com.ibm.watsonx.ai.tool.ToolRequest;
import com.ibm.watsonx.ai.tool.ToolRestClient;
import com.ibm.watsonx.ai.tool.ToolService.Resources;
import com.ibm.watsonx.ai.tool.UtilityTool;

import io.quarkiverse.langchain4j.watsonx.runtime.client.QuarkusRestClientConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.client.ToolRestApi;
import io.quarkiverse.langchain4j.watsonx.runtime.client.filter.BearerTokenHeaderFactory;
import io.quarkiverse.langchain4j.watsonx.runtime.client.logger.WatsonxClientLogger;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public final class QuarkusToolRestClient extends ToolRestClient {

    private final ToolRestApi client;

    QuarkusToolRestClient(Builder builder) {
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

            client = restClientBuilder.build(ToolRestApi.class);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Resources getAll(String transactionId) {
        var requestId = UUID.randomUUID().toString();
        return retryOn(requestId, new Callable<Resources>() {
            @Override
            public Resources call() throws Exception {
                return client.getAll(requestId, transactionId);
            }
        });
    }

    @Override
    public UtilityTool getByName(String transactionId, String name) {
        var requestId = UUID.randomUUID().toString();
        return retryOn(requestId, new Callable<UtilityTool>() {
            @Override
            public UtilityTool call() throws Exception {
                return client.getByName(requestId, transactionId, name);
            }
        });
    }

    @Override
    public String run(String transactionId, ToolRequest request) {
        var requestId = UUID.randomUUID().toString();
        return retryOn(requestId, new Callable<String>() {
            @Override
            public String call() throws Exception {
                return (String) client.run(requestId, transactionId, request).get("output");
            }
        });
    }

    public static final class QuarkusToolRestClientBuilderFactory implements ToolRestClientBuilderFactory {
        @Override
        public Builder get() {
            return new QuarkusToolRestClient.Builder();
        }
    }

    static final class Builder extends ToolRestClient.Builder {
        @Override
        public ToolRestClient build() {
            return new QuarkusToolRestClient(this);
        }
    }
}
