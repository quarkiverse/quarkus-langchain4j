package io.quarkiverse.langchain4j.watsonx.bean;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TextGenerationResponse(List<Result> results) {
    public record Result(
            @JsonProperty("generated_text") String generatedText,
            @JsonProperty("generated_token_count") int generatedTokenCount,
            @JsonProperty("input_token_count") int inputTokenCount,
            @JsonProperty("stop_reason") String stopReason) {
    }
}
