package io.quarkiverse.langchain4j.vertexai.runtime.gemini;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PredictResponse(List<Prediction> predictions) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Prediction(Embeddings embeddings) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Embeddings(Statistics statistics, List<Float> values) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Statistics(boolean truncated, @JsonProperty("token_count") int tokenCount) {
    }
}
