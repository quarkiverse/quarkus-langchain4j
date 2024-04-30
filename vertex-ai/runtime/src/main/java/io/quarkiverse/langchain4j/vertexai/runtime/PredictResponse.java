package io.quarkiverse.langchain4j.vertexai.runtime;

import java.util.List;

public record PredictResponse(List<Predictions> predictions, Metadata metadata) {

    public record Predictions(List<Candidate> candidates) {

    }

    public record Candidate(String content, String author) {

    }

    public record Metadata(TokenMetadata tokenMetadata) {

        public record TokenMetadata(TokenCount inputTokenCount, TokenCount outputTokenCount) {

            public record TokenCount(Integer totalTokens) {

            }
        }
    }
}
