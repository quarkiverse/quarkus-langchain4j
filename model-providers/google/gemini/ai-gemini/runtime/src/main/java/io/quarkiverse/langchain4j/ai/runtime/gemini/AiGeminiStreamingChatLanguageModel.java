package io.quarkiverse.langchain4j.ai.runtime.gemini;

import static dev.langchain4j.http.client.HttpMethod.POST;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.jboss.resteasy.reactive.client.SseEvent;
import org.jboss.resteasy.reactive.client.api.LoggingScope;

import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ResponseFormat;
import io.quarkiverse.langchain4j.gemini.common.GeminiStreaminChatLanguageModel;
import io.quarkiverse.langchain4j.gemini.common.GenerateContentRequest;
import io.quarkiverse.langchain4j.gemini.common.GenerateContentResponse;
import io.quarkiverse.langchain4j.gemini.common.ModelAuthProviderFilter;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.smallrye.mutiny.Multi;

public class AiGeminiStreamingChatLanguageModel extends GeminiStreaminChatLanguageModel {

    private final AiGeminiRestApi.ApiMetadata apiMetadata;
    private final AiGeminiRestApi restApi;

    private AiGeminiStreamingChatLanguageModel(Builder builder) {
        super(builder.modelId, builder.temperature, builder.maxOutputTokens, builder.topK, builder.topP, builder.responseFormat,
                builder.listeners);

        this.apiMetadata = AiGeminiRestApi.ApiMetadata
                .builder()
                .modelId(builder.modelId)
                .key(builder.key)
                .build();

        try {
            String baseUrl = builder.baseUrl.orElse("https://generativelanguage.googleapis.com");
            var restApiBuilder = QuarkusRestClientBuilder.newBuilder()
                    .baseUri(new URI(baseUrl))
                    .connectTimeout(builder.timeout.toSeconds(), TimeUnit.SECONDS)
                    .readTimeout(builder.timeout.toSeconds(), TimeUnit.SECONDS);

            if (builder.logRequests || builder.logResponses) {
                restApiBuilder.loggingScope(LoggingScope.REQUEST_RESPONSE);
                restApiBuilder.clientLogger(new AiGeminiRestApi.AiClientLogger(builder.logRequests,
                        builder.logResponses));
            }

            if (builder.key == null) {
                restApiBuilder.register(new ModelAuthProviderFilter(builder.modelId));
            }

            restApi = restApiBuilder.build(AiGeminiRestApi.class);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected Multi<SseEvent<GenerateContentResponse>> generateStreamContext(GenerateContentRequest request) {
        return restApi.generateContentStream(request, apiMetadata, "sse");
    }

    private static final String API_KEY_HEADER_NAME = "x-goog-api-key";

    private HttpRequest buildHttpRequest(String url, String apiKey, String jsonBody) {
        return HttpRequest.builder()
                .method(POST)
                .url(url)
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "LangChain4j")
                .addHeader(API_KEY_HEADER_NAME, apiKey)
                .body(jsonBody)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public static final class Builder {

        private String configName;
        private Optional<String> baseUrl = Optional.empty();
        private String modelId;
        private String key;
        private Double temperature;
        private Integer maxOutputTokens;
        private Integer topK;
        private Double topP;
        private ResponseFormat responseFormat;
        private Duration timeout = Duration.ofSeconds(10);
        private Boolean logRequests = false;
        private Boolean logResponses = false;
        private List<ChatModelListener> listeners = Collections.emptyList();

        public Builder configName(String configName) {
            this.configName = configName;
            return this;
        }

        public Builder baseUrl(Optional<String> baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder key(String key) {
            this.key = key;
            return this;
        }

        public Builder modelId(String modelId) {
            this.modelId = modelId;
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

        public AiGeminiStreamingChatLanguageModel build() {
            return new AiGeminiStreamingChatLanguageModel(this);
        }
    }
}
