package io.quarkiverse.langchain4j.watsonx;

import java.net.URL;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.jboss.resteasy.reactive.client.api.LoggingScope;

import io.quarkiverse.langchain4j.watsonx.client.WatsonxRestApi;
import io.quarkiverse.langchain4j.watsonx.client.filter.BearerTokenHeaderFactory;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public abstract class Watsonx {

    protected final String modelId, projectId, version;
    protected final WatsonxRestApi client;

    public Watsonx(Builder<?> builder) {
        QuarkusRestClientBuilder restClientBuilder = QuarkusRestClientBuilder.newBuilder()
                .baseUrl(builder.url)
                .clientHeadersFactory(new BearerTokenHeaderFactory(builder.tokenGenerator))
                .connectTimeout(builder.timeout.toSeconds(), TimeUnit.SECONDS)
                .readTimeout(builder.timeout.toSeconds(), TimeUnit.SECONDS);

        if (builder.logRequests || builder.logResponses) {
            restClientBuilder.loggingScope(LoggingScope.REQUEST_RESPONSE);
            restClientBuilder.clientLogger(new WatsonxRestApi.WatsonClientLogger(
                    builder.logRequests,
                    builder.logResponses));
        }

        this.client = restClientBuilder.build(WatsonxRestApi.class);
        this.modelId = builder.modelId;
        this.projectId = builder.projectId;
        this.version = builder.version;
    }

    public WatsonxRestApi getClient() {
        return client;
    }

    public String getModelId() {
        return modelId;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getSpaceId() {
        return spaceId;
    }

    public String getVersion() {
        return version;
    }

    @SuppressWarnings("unchecked")
    public static abstract class Builder<T extends Builder<T>> {

        protected String modelId;
        protected String version;
        protected String projectId;
        protected Duration timeout;
        protected URL url;
        protected boolean logResponses;
        protected boolean logRequests;
        protected WatsonxTokenGenerator tokenGenerator;

        public T modelId(String modelId) {
            this.modelId = modelId;
            return (T) this;
        }

        public T version(String version) {
            this.version = version;
            return (T) this;
        }

        public T projectId(String projectId) {
            this.projectId = projectId;
            return (T) this;
        }

        public T url(URL url) {
            this.url = url;
            return (T) this;
        }

        public T timeout(Duration timeout) {
            this.timeout = timeout;
            return (T) this;
        }

        public T tokenGenerator(WatsonxTokenGenerator tokenGenerator) {
            this.tokenGenerator = tokenGenerator;
            return (T) this;
        }

        public T logRequests(boolean logRequests) {
            this.logRequests = logRequests;
            return (T) this;
        }

        public T logResponses(boolean logResponses) {
            this.logResponses = logResponses;
            return (T) this;
        }
    }
}
