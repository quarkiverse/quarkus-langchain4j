package io.quarkiverse.langchain4j.gemini.common;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;

public final class GenerateContentResponseHandler {

    private GenerateContentResponseHandler() {
    }

    public static String getText(GenerateContentResponse response) {
        GenerateContentResponse.FinishReason finishReason = getFinishReason(response);
        if (finishReason == GenerateContentResponse.FinishReason.SAFETY) {
            throw new IllegalArgumentException("The response is blocked due to safety reason.");
        } else if (finishReason == GenerateContentResponse.FinishReason.RECITATION) {
            throw new IllegalArgumentException("The response is blocked due to unauthorized citations.");
        }

        StringBuilder text = new StringBuilder();
        if (response.candidates() != null && !response.candidates().isEmpty()) {
            if (response.candidates().get(0).content() != null) {
                List<GenerateContentResponse.Candidate.Part> parts = response.candidates().get(0).content().parts();
                if (parts != null && !parts.isEmpty()) {
                    for (GenerateContentResponse.Candidate.Part part : parts) {
                        if (part.thought() == null || !part.thought()) {
                            text.append(part.text());
                        }
                    }
                }
            }
        }
        return text.toString();
    }

    public static String getThoughts(GenerateContentResponse response) {
        GenerateContentResponse.FinishReason finishReason = getFinishReason(response);
        if (finishReason == GenerateContentResponse.FinishReason.SAFETY) {
            throw new IllegalArgumentException("The response is blocked due to safety reason.");
        } else if (finishReason == GenerateContentResponse.FinishReason.RECITATION) {
            throw new IllegalArgumentException("The response is blocked due to unauthorized citations.");
        }

        StringBuilder text = new StringBuilder();
        if (response.candidates() != null && !response.candidates().isEmpty()) {
            if (response.candidates().get(0).content() != null) {
                List<GenerateContentResponse.Candidate.Part> parts = response.candidates().get(0).content().parts();
                if (parts != null && !parts.isEmpty()) {
                    for (GenerateContentResponse.Candidate.Part part : parts) {
                        if (part.thought() != null && part.thought()) {
                            text.append(part.text());
                        }
                    }
                }
            }
        }
        return text.toString();
    }

    public static GenerateContentResponse.FinishReason getFinishReason(GenerateContentResponse response) {
        if (response.candidates().size() != 1) {
            throw new IllegalArgumentException(
                    String.format(
                            "This response should have exactly 1 candidate, but it has %s.",
                            response.candidates().size()));
        }
        return response.candidates().get(0).finishReason();
    }

    public static TokenUsage getTokenUsage(GenerateContentResponse.UsageMetadata usageMetadata) {
        return new TokenUsage(
                usageMetadata.promptTokenCount(),
                usageMetadata.candidatesTokenCount(),
                usageMetadata.totalTokenCount());
    }

    public static List<ToolExecutionRequest> getToolExecutionRequests(GenerateContentResponse response) {
        List<GenerateContentResponse.Candidate.Part> parts = response.candidates().get(0).content().parts();
        List<ToolExecutionRequest> toolExecutionRequests = new ArrayList<>();
        for (GenerateContentResponse.Candidate.Part part : parts) {
            FunctionCall functionCall = part.functionCall();
            if (functionCall == null) {
                continue;
            }
            toolExecutionRequests.add(toToolExecutionRequest(part));
        }
        return toolExecutionRequests;
    }

    private static ToolExecutionRequest toToolExecutionRequest(GenerateContentResponse.Candidate.Part part) {
        try {
            return ToolExecutionRequest.builder()
                    .name(part.functionCall().name())
                    .arguments(QuarkusJsonCodecFactory.ObjectMapperHolder.MAPPER
                            .writeValueAsString(part.functionCall().args()))
                    .id(part.thoughtSignature())
                    .build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Unable to parse tool call response", e);
        }
    }
}
