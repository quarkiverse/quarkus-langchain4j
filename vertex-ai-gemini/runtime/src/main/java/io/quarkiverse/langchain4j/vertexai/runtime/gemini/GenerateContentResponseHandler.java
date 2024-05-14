package io.quarkiverse.langchain4j.vertexai.runtime.gemini;

import java.util.List;

import dev.langchain4j.model.output.TokenUsage;

final class GenerateContentResponseHandler {

    private GenerateContentResponseHandler() {
    }

    static String getText(GenerateContentResponse response) {
        GenerateContentResponse.FinishReason finishReason = getFinishReason(response);
        if (finishReason == GenerateContentResponse.FinishReason.SAFETY) {
            throw new IllegalArgumentException("The response is blocked due to safety reason.");
        } else if (finishReason == GenerateContentResponse.FinishReason.RECITATION) {
            throw new IllegalArgumentException("The response is blocked due to unauthorized citations.");
        }

        StringBuilder text = new StringBuilder();
        List<GenerateContentResponse.Candidate.Part> parts = response.candidates().get(0).content().parts();
        for (GenerateContentResponse.Candidate.Part part : parts) {
            text.append(part.text());
        }

        return text.toString();
    }

    static GenerateContentResponse.FinishReason getFinishReason(GenerateContentResponse response) {
        if (response.candidates().size() != 1) {
            throw new IllegalArgumentException(
                    String.format(
                            "This response should have exactly 1 candidate, but it has %s.",
                            response.candidates().size()));
        }
        return response.candidates().get(0).finishReason();
    }

    static TokenUsage getTokenUsage(GenerateContentResponse.UsageMetadata usageMetadata) {
        return new TokenUsage(
                usageMetadata.promptTokenCount(),
                usageMetadata.candidatesTokenCount(),
                usageMetadata.totalTokenCount());
    }
}
