package io.quarkiverse.langchain4j.ai.runtime.gemini;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.quarkiverse.langchain4j.gemini.common.Content;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import org.jboss.resteasy.reactive.client.api.LoggingScope;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class AiGeminiEmbeddingModel implements EmbeddingModel {

    private final AiGeminiRestApi restApi;
    private final AiGeminiRestApi.ApiMetadata apiMetadata;

    public AiGeminiEmbeddingModel(Builder builder) {
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
            restApi = restApiBuilder.build(AiGeminiRestApi.class);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Response<Embedding> embed(String text) {

        Content.Part part = Content.Part.ofText(text);
        Content content = Content.ofPart(part);

        EmbedContentRequest embedContentRequest = new EmbedContentRequest(content,
                null, null, null);

        EmbedContentResponse embedContentResponse = restApi.embedContent(embedContentRequest, this.apiMetadata);

        return Response.from(Embedding.from(embedContentResponse.embedding().values()));
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        return null;
    }

    public static final class Builder {
        private Optional<String> baseUrl = Optional.empty();
        private String modelId;
        private String key;
        private Duration timeout = Duration.ofSeconds(10);
        private Boolean logRequests = false;
        private Boolean logResponses = false;

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
    }
}
