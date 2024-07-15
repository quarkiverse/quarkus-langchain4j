package io.quarkiverse.langchain4j.ollama;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

public class OllamaEmbeddingModel implements EmbeddingModel {

    private final QuarkusOllamaClient client;
    private final String model;

    private OllamaEmbeddingModel(Builder builder) {
        client = new QuarkusOllamaClient(builder.baseUrl, builder.timeout, builder.logRequests, builder.logResponses);
        model = builder.model;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        List<Embedding> embeddings = new ArrayList<>();

        textSegments.forEach(textSegment -> {
            EmbeddingRequest request = EmbeddingRequest.builder()
                    .model(model)
                    .prompt(textSegment.text())
                    .build();

            EmbeddingResponse response = client.embed(request);

            embeddings.add(Embedding.from(response.getEmbedding()));
        });

        return Response.from(embeddings);
    }

    public static final class Builder {
        private String baseUrl = "http://localhost:11434";
        private Duration timeout = Duration.ofSeconds(10);
        private String model;

        private boolean logRequests = false;
        private boolean logResponses = false;

        private Builder() {
        }

        public Builder baseUrl(String val) {
            baseUrl = val;
            return this;
        }

        public Builder timeout(Duration val) {
            this.timeout = val;
            return this;
        }

        public Builder model(String val) {
            model = val;
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

        public OllamaEmbeddingModel build() {
            return new OllamaEmbeddingModel(this);
        }
    }

}
