package io.quarkiverse.langchain4j.watsonx.bean;

public record TextGenerationRequest(
        String modelId,
        String spaceId,
        String projectId,
        String input,
        TextGenerationParameters parameters) {
}
