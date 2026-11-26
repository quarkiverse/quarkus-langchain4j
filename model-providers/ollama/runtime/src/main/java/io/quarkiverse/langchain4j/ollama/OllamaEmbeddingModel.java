package io.quarkiverse.langchain4j.ollama;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

public class OllamaEmbeddingModel implements EmbeddingModel {

    /**
     * How many segments go into one {@code /api/embed} request unless configured otherwise. Ollama documents no
     * limit on the {@code input} array, but an unbounded request grows with the corpus being ingested and can
     * exceed what a reverse proxy in front of Ollama accepts, so the batch is capped like other providers do.
     */
    public static final int DEFAULT_MAX_SEGMENTS_PER_BATCH = 100;

    private final OllamaClient client;
    private final String model;
    private final int maxSegmentsPerBatch;

    private OllamaEmbeddingModel(Builder builder) {
        client = new OllamaClient(builder.baseUrl, builder.timeout, builder.logRequests, builder.logResponses,
                false, builder.configName, builder.tlsConfigurationName);
        model = builder.model;
        if (builder.maxSegmentsPerBatch < 1) {
            throw new IllegalArgumentException("maxSegmentsPerBatch must be at least 1");
        }
        maxSegmentsPerBatch = builder.maxSegmentsPerBatch;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Embeds the segments in batches of at most {@code maxSegmentsPerBatch} per {@code /api/embed} call: the endpoint
     * accepts an array of inputs and returns one vector per input, in input order, which is also how the upstream
     * langchain4j Ollama embedding model uses it.
     */
    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        List<String> inputs = new ArrayList<>(textSegments.size());
        for (TextSegment textSegment : textSegments) {
            inputs.add(textSegment.text());
        }

        List<Embedding> embeddings = new ArrayList<>(inputs.size());
        TokenUsage tokenUsage = null;
        for (int from = 0; from < inputs.size(); from += maxSegmentsPerBatch) {
            List<String> batch = inputs.subList(from, Math.min(from + maxSegmentsPerBatch, inputs.size()));

            EmbeddingRequest request = EmbeddingRequest.builder()
                    .model(model)
                    .input(batch)
                    .build();

            EmbeddingResponse response = client.embedding(request);

            if (response.getEmbeddings() != null) {
                for (float[] embedding : response.getEmbeddings()) {
                    embeddings.add(Embedding.from(embedding));
                }
            }
            if (response.getPromptEvalCount() != null) {
                tokenUsage = TokenUsage.sum(tokenUsage, new TokenUsage(response.getPromptEvalCount()));
            }
        }

        return Response.from(embeddings, tokenUsage);
    }

    public static final class Builder {
        private String baseUrl = "http://localhost:11434";
        private String tlsConfigurationName;
        private Duration timeout = Duration.ofSeconds(10);
        private String model;
        private int maxSegmentsPerBatch = DEFAULT_MAX_SEGMENTS_PER_BATCH;

        private boolean logRequests = false;
        private boolean logResponses = false;
        private String configName;

        private Builder() {
        }

        public Builder baseUrl(String val) {
            baseUrl = val;
            return this;
        }

        public Builder tlsConfigurationName(String tlsConfigurationName) {
            this.tlsConfigurationName = tlsConfigurationName;
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

        /**
         * The maximum number of segments sent in one {@code /api/embed} request; larger inputs to
         * {@link #embedAll(List)} are split into consecutive requests.
         */
        public Builder maxSegmentsPerBatch(int maxSegmentsPerBatch) {
            this.maxSegmentsPerBatch = maxSegmentsPerBatch;
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

        public Builder configName(String configName) {
            this.configName = configName;
            return this;
        }

        public OllamaEmbeddingModel build() {
            return new OllamaEmbeddingModel(this);
        }
    }

}
