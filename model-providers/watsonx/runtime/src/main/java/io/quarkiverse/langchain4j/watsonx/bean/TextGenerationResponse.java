package io.quarkiverse.langchain4j.watsonx.bean;

import java.util.List;

public record TextGenerationResponse(List<Result> results) {

    public record Result(
            String generatedText,
            int generatedTokenCount,
            int inputTokenCount,
            String stopReason) {
    }
}
