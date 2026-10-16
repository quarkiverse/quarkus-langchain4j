package io.quarkiverse.langchain4j.watsonx.runtime.client.impl;

import static io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils.retryOn;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.jboss.resteasy.reactive.client.api.LoggingScope;

import com.ibm.watsonx.ai.core.exception.WatsonxException;
import com.ibm.watsonx.ai.project.Project;
import com.ibm.watsonx.ai.project.ProjectRestClient;

import io.quarkiverse.langchain4j.watsonx.runtime.client.ProjectRestApi;
import io.quarkiverse.langchain4j.watsonx.runtime.client.QuarkusRestClientConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.client.filter.BearerTokenHeaderFactory;
import io.quarkiverse.langchain4j.watsonx.runtime.client.logger.WatsonxClientLogger;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public final class QuarkusProjectRestClient extends ProjectRestClient {

    private final ProjectRestApi client;

    QuarkusProjectRestClient(Builder builder) {
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

            client = restClientBuilder.build(ProjectRestApi.class);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<Project> findProject(String projectId) {
        try {
            return Optional.ofNullable(retryOn(null, new Callable<Project>() {
                @Override
                public Project call() throws Exception {
                    return client.findProject(projectId);
                }
            }));
        } catch (WatsonxException e) {
            if (e.statusCode() == 404)
                return Optional.empty();
            throw e;
        }
    }

    public static final class QuarkusProjectRestClientBuilderFactory implements ProjectRestClientBuilderFactory {
        @Override
        public Builder get() {
            return new QuarkusProjectRestClient.Builder();
        }
    }

    static final class Builder extends ProjectRestClient.Builder {
        @Override
        public ProjectRestClient build() {
            return new QuarkusProjectRestClient(this);
        }
    }
}
