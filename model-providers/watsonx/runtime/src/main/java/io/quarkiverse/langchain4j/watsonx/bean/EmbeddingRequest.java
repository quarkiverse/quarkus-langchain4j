package io.quarkiverse.langchain4j.watsonx.bean;

import java.util.List;

public record EmbeddingRequest(String modelId, String projectId, List<String> inputs) {

    public EmbeddingRequest of(String modelId, String projectId, String input) {
        return new EmbeddingRequest(modelId, projectId, List.of(input));
    }
}
