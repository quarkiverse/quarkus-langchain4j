package io.quarkiverse.langchain4j.vertexai.runtime.gemini;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PredictResponse {

    private final List<Prediction> predictions;

    public PredictResponse(List<Prediction> predictions) {
        this.predictions = predictions;
    }

    public List<Prediction> getPredictions() {
        return predictions;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Prediction {
        private final Embeddings embeddings;

        public Prediction(Embeddings embeddings) {
            this.embeddings = embeddings;
        }

        public Embeddings getEmbeddings() {
            return embeddings;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Embeddings {
        private final Statistics statistics;
        private final List<Float> values;

        public Embeddings(Statistics statistics, List<Float> values) {
            this.statistics = statistics;
            this.values = values;
        }

        public Statistics getStatistics() {
            return statistics;
        }

        public List<Float> getValues() {
            return values;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Statistics {
        private final boolean truncated;
        @JsonProperty("token_count")
        private final int tokenCount;

        public Statistics(boolean truncated, int tokenCount) {
            this.truncated = truncated;
            this.tokenCount = tokenCount;
        }

        public boolean isTruncated() {
            return truncated;
        }

        public int getTokenCount() {
            return tokenCount;
        }
    }
}
