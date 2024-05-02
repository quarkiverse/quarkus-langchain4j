package io.quarkiverse.langchain4j.vertexai.runtime.gemini;

import dev.langchain4j.model.output.FinishReason;

final class FinishReasonMapper {

    private FinishReasonMapper() {
    }

    static FinishReason map(GenerateContentResponse.FinishReason finishReason) {
        return switch (finishReason) {
            case STOP -> FinishReason.STOP;
            case MAX_TOKENS -> FinishReason.LENGTH;
            case SAFETY -> FinishReason.CONTENT_FILTER;
            default -> FinishReason.OTHER;
        };
    }
}
