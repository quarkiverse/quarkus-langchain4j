package io.quarkiverse.langchain4j.mockapi;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import io.quarkiverse.langchain4j.mockapi.internal.model.EmbeddingReplyImpl;

public non-sealed interface EmbeddingReply extends Reply {

    static EmbeddingReply of(float[]... vectors) {
        return new EmbeddingReplyImpl(Arrays.asList(vectors), null, null);
    }

    static EmbeddingReply derived(int dimension) {
        return new EmbeddingReplyImpl(null, dimension, null);
    }

    List<float[]> vectors();

    Integer derivedDimension();

    EmbeddingReply delay(Duration delay);
}
