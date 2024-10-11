package io.quarkiverse.langchain4j.watsonx.bean;

public record TextGenerationRequest(
        String modelId,
        String projectId,
        String input,
        TextGenerationParameters parameters) {
}
