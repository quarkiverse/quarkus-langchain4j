package io.quarkiverse.langchain4j.ai.runtime.gemini;

import java.util.List;

public record GenerateContentResponse(List<Candidate> candidates, UsageMetadata usageMetadata) {

    public record Candidate(Content content, FinishReason finishReason) {

        public record Content(List<Part> parts) {

        }

        public record Part(String text, FunctionCall functionCall) {

        }

    }

    public record UsageMetadata(Integer promptTokenCount, Integer candidatesTokenCount, Integer totalTokenCount) {

    }

    public enum FinishReason {

        FINISH_REASON_UNSPECIFIED,
        STOP,
        MAX_TOKENS,
        SAFETY,
        RECITATION,
        OTHER,
        UNRECOGNIZED
    }
}
