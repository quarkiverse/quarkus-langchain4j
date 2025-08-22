package io.quarkiverse.langchain4j.vertexai.runtime.gemini;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.jboss.resteasy.reactive.client.api.LoggingScope;

import io.quarkiverse.langchain4j.gemini.common.EmbedContentRequest;
import io.quarkiverse.langchain4j.gemini.common.EmbedContentRequests;
import io.quarkiverse.langchain4j.gemini.common.EmbedContentResponse;
import io.quarkiverse.langchain4j.gemini.common.EmbedContentResponses;
import io.quarkiverse.langchain4j.gemini.common.GeminiEmbeddingModel;
import io.quarkiverse.langchain4j.gemini.common.ModelAuthProviderFilter;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public class VertexAiGeminiEmbeddingModel extends GeminiEmbeddingModel {

    private final VertxAiGeminiRestApi restApi;
    private final VertxAiGeminiRestApi.ApiMetadata apiMetadata;

    public VertexAiGeminiEmbeddingModel(Builder builder) {
        super(builder.modelId, builder.dimension, builder.taskType);

        this.apiMetadata = VertxAiGeminiRestApi.ApiMetadata
                .builder()
                .modelId(builder.modelId)
                .location(builder.location)
                .projectId(builder.projectId)
                .publisher(builder.publisher)
                .build();

        try {
            String baseUrl = builder.baseUrl.orElse("https://generativelanguage.googleapis.com");
            var restApiBuilder = QuarkusRestClientBuilder.newBuilder()
                    .baseUri(new URI(baseUrl))
                    .connectTimeout(builder.timeout.toSeconds(), TimeUnit.SECONDS)
                    .readTimeout(builder.timeout.toSeconds(), TimeUnit.SECONDS);

            if (builder.proxy != null) {
                if (builder.proxy.type() != Proxy.Type.HTTP) {
                    throw new IllegalArgumentException("Only HTTP type proxy is supported");
                }
                if (!(builder.proxy.address() instanceof InetSocketAddress)) {
                    throw new IllegalArgumentException("Unsupported proxy type");
                }
                InetSocketAddress socketAddress = (InetSocketAddress) builder.proxy.address();
                restApiBuilder.proxyAddress(socketAddress.getHostName(), socketAddress.getPort());
            }

            if (builder.logRequests || builder.logResponses) {
                restApiBuilder.loggingScope(LoggingScope.REQUEST_RESPONSE);
                restApiBuilder.clientLogger(new VertxAiGeminiRestApi.VertxAiClientLogger(builder.logRequests,
                        builder.logResponses));
            }
            restApiBuilder.register(new ModelAuthProviderFilter(builder.modelId));
            restApi = restApiBuilder.build(VertxAiGeminiRestApi.class);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected EmbedContentResponse embedContent(EmbedContentRequest embedContentRequest) {
        return restApi.embedContent(embedContentRequest, apiMetadata);
    }

    @Override
    protected EmbedContentResponses batchEmbedContents(EmbedContentRequests embedContentRequests) {
        return restApi.batchEmbedContents(embedContentRequests, apiMetadata);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private Optional<String> baseUrl = Optional.empty();
        private String projectId;
        private String location;
        private String modelId;
        private String publisher;
        private Integer dimension;
        private String taskType;
        private Duration timeout = Duration.ofSeconds(10);
        private Boolean logRequests = false;
        private Boolean logResponses = false;
        private Proxy proxy;

        public Builder baseUrl(Optional<String> baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder location(String location) {
            this.location = location;
            return this;
        }

        public Builder modelId(String modelId) {
            this.modelId = modelId;
            return this;
        }

        public Builder publisher(String publisher) {
            this.publisher = publisher;
            return this;
        }

        public Builder dimension(Integer dimension) {
            this.dimension = dimension;
            return this;
        }

        public Builder taskType(String taskType) {
            this.taskType = taskType;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder logRequests(boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses(boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public Builder proxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        public VertexAiGeminiEmbeddingModel build() {
            return new VertexAiGeminiEmbeddingModel(this);
        }
    }
}
