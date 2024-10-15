package io.quarkiverse.langchain4j.watsonx.bean;

import java.util.List;

public record EmbeddingRequest(String modelId, String projectId, List<String> inputs, EmbeddingParameters parameters) {

    public EmbeddingRequest of(String modelId, String projectId, String input, EmbeddingParameters parameters) {
        return new EmbeddingRequest(modelId, projectId, List.of(input), parameters);
    }
}
