package io.quarkiverse.langchain4j.ai.runtime.gemini;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.jboss.resteasy.reactive.client.api.LoggingScope;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.quarkiverse.langchain4j.gemini.common.Content;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public class AiGeminiEmbeddingModel implements EmbeddingModel {

    private final AiGeminiRestApi restApi;
    private final AiGeminiRestApi.ApiMetadata apiMetadata;

    private final Integer dimension;

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

            this.dimension = builder.dimension;
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

        EmbedContentRequest embedContentRequest = getEmbedContentRequest(this.apiMetadata.modelId, text);

        EmbedContentResponse embedContentResponse = restApi.embedContent(embedContentRequest, this.apiMetadata);

        return Response.from(Embedding.from(embedContentResponse.embedding().values()));
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        List<EmbedContentRequest> embedContentRequests = textSegments.stream()
                .map(textSegment -> getEmbedContentRequest(this.apiMetadata.modelId, textSegment.text()))
                .toList();

        EmbedContentResponses embedContentResponses = restApi.batchEmbedContents(
                new EmbedContentRequests(embedContentRequests),
                this.apiMetadata);

        List<Embedding> embeddings = embedContentResponses.embeddings()
                .stream()
                .map(embedding -> Embedding.from(embedding.values()))
                .toList();
        return Response.from(embeddings);
    }

    private EmbedContentRequest getEmbedContentRequest(String model, String text) {
        Content.Part part = Content.Part.ofText(text);
        Content content = Content.ofPart(part);

        EmbedContentRequest embedContentRequest = new EmbedContentRequest("models/" + model, content,
                null, null, this.dimension);
        return embedContentRequest;
    }

    public static final class Builder {
        private Optional<String> baseUrl = Optional.empty();
        private String modelId;
        private String key;
        private Integer dimension;
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

        public Builder dimension(Integer dimension) {
            this.dimension = dimension;
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

        public AiGeminiEmbeddingModel build() {
            return new AiGeminiEmbeddingModel(this);
        }
    }
}
