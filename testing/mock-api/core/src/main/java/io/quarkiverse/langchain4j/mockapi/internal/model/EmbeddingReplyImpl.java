package io.quarkiverse.langchain4j.mockapi.internal.model;

import java.time.Duration;
import java.util.List;

import io.quarkiverse.langchain4j.mockapi.EmbeddingReply;

public record EmbeddingReplyImpl(List<float[]> vectors, Integer derivedDimension, Duration delay) implements EmbeddingReply {

    @Override
    public EmbeddingReply delay(Duration delay) {
        return new EmbeddingReplyImpl(vectors, derivedDimension, delay);
    }
}
