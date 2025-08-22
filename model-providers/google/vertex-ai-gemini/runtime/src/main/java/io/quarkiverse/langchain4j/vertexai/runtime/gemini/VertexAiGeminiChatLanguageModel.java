package io.quarkiverse.langchain4j.vertexai.runtime.gemini;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.jboss.resteasy.reactive.client.api.LoggingScope;

import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ResponseFormat;
import io.quarkiverse.langchain4j.gemini.common.GeminiChatLanguageModel;
import io.quarkiverse.langchain4j.gemini.common.GenerateContentRequest;
import io.quarkiverse.langchain4j.gemini.common.GenerateContentResponse;
import io.quarkiverse.langchain4j.gemini.common.ModelAuthProviderFilter;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public class VertexAiGeminiChatLanguageModel extends GeminiChatLanguageModel {

    private final VertxAiGeminiRestApi.ApiMetadata apiMetadata;
    private final VertxAiGeminiRestApi restApi;

    private VertexAiGeminiChatLanguageModel(Builder builder) {
        super(builder.modelId, builder.temperature, builder.maxOutputTokens, builder.topK, builder.topP, builder.responseFormat,
                builder.listeners);

        this.apiMetadata = VertxAiGeminiRestApi.ApiMetadata
                .builder()
                .modelId(builder.modelId)
                .location(builder.location)
                .projectId(builder.projectId)
                .publisher(builder.publisher)
                .build();

        try {
            String baseUrl = builder.baseUrl.orElse(String.format("https://%s-aiplatform.googleapis.com", builder.location));
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
    protected GenerateContentResponse generateContext(GenerateContentRequest request) {
        return restApi.generateContent(request, apiMetadata);
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
        private Double temperature;
        private Integer maxOutputTokens;
        private Integer topK;
        private Double topP;
        private ResponseFormat responseFormat;
        private Duration timeout = Duration.ofSeconds(10);
        private Boolean logRequests = false;
        private Boolean logResponses = false;
        private List<ChatModelListener> listeners = Collections.emptyList();
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

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        public Builder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public Builder responseFormat(ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
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

        public Builder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public Builder proxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        public VertexAiGeminiChatLanguageModel build() {
            return new VertexAiGeminiChatLanguageModel(this);
        }
    }

}
