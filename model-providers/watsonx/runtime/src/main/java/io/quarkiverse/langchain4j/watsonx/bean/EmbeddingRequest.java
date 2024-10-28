package io.quarkiverse.langchain4j.watsonx.bean;

import java.util.List;

public record EmbeddingRequest(String modelId, String spaceId, String projectId, List<String> inputs,
        EmbeddingParameters parameters) {
    public EmbeddingRequest of(String modelId, String spaceId, String projectId, String input, EmbeddingParameters parameters) {
        return new EmbeddingRequest(modelId, spaceId, projectId, List.of(input), parameters);
    }
}
