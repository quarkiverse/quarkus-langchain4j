package io.quarkiverse.langchain4j.gemini.common;

import dev.langchain4j.model.output.FinishReason;

public final class FinishReasonMapper {

    private FinishReasonMapper() {
    }

    public static FinishReason map(GenerateContentResponse.FinishReason finishReason) {
        return switch (finishReason) {
            case STOP -> FinishReason.STOP;
            case MAX_TOKENS -> FinishReason.LENGTH;
            case SAFETY -> FinishReason.CONTENT_FILTER;
            default -> FinishReason.OTHER;
        };
    }
}
