package io.quarkiverse.langchain4j.vertexai.runtime.gemini;

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
import io.quarkiverse.langchain4j.gemini.common.EmbedContentRequest;
import io.quarkiverse.langchain4j.gemini.common.EmbedContentRequests;
import io.quarkiverse.langchain4j.gemini.common.EmbedContentResponse;
import io.quarkiverse.langchain4j.gemini.common.EmbedContentResponses;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public class VertexAiGeminiEmbeddingModel implements EmbeddingModel {

    private final VertxAiGeminiRestApi restApi;
    private final VertxAiGeminiRestApi.ApiMetadata apiMetadata;

    private final Integer dimension;
    private final String taskType;

    public VertexAiGeminiEmbeddingModel(Builder builder) {
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

            if (builder.logRequests || builder.logResponses) {
                restApiBuilder.loggingScope(LoggingScope.REQUEST_RESPONSE);
                restApiBuilder.clientLogger(new VertxAiGeminiRestApi.VertxAiClientLogger(builder.logRequests,
                        builder.logResponses));
            }

            this.dimension = builder.dimension;
            this.taskType = builder.taskType;
            restApi = restApiBuilder.build(VertxAiGeminiRestApi.class);
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

        EmbedContentRequest.TaskType embedTaskType = null;
        if (this.taskType != null) {
            embedTaskType = EmbedContentRequest.TaskType.valueOf(this.taskType);
        }

        EmbedContentRequest embedContentRequest = new EmbedContentRequest("models/" + model, content,
                embedTaskType, null, this.dimension);
        return embedContentRequest;
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

        public VertexAiGeminiEmbeddingModel build() {
            return new VertexAiGeminiEmbeddingModel(this);
        }
    }
}
