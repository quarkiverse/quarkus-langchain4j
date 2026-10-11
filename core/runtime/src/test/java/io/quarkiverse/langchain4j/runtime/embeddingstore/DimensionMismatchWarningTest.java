package io.quarkiverse.langchain4j.runtime.embeddingstore;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.langchain4j.data.embedding.Embedding;

class DimensionMismatchWarningTest {

    private static final String PROPERTY = "quarkus.langchain4j.pgvector.dimension";

    @Test
    void shouldReturnMessageOnFirstMismatch() {
        DimensionMismatchWarning warning = new DimensionMismatchWarning(384, PROPERTY);

        Optional<String> message = warning.check(embedding(1536));

        assertTrue(message.isPresent());
        assertTrue(message.get().contains("1536"), message.get());
        assertTrue(message.get().contains("384"), message.get());
        assertTrue(message.get().contains(PROPERTY), message.get());
    }

    @Test
    void shouldNotWarnMoreThanOnce() {
        DimensionMismatchWarning warning = new DimensionMismatchWarning(384, PROPERTY);

        assertTrue(warning.check(embedding(1536)).isPresent());
        assertFalse(warning.check(embedding(1536)).isPresent());
        assertFalse(warning.check(embedding(512)).isPresent());
    }

    @Test
    void shouldNotWarnWhenDimensionsMatch() {
        DimensionMismatchWarning warning = new DimensionMismatchWarning(384, PROPERTY);

        assertFalse(warning.check(embedding(384)).isPresent());
    }

    @Test
    void shouldStillWarnAfterAMatchingEmbedding() {
        DimensionMismatchWarning warning = new DimensionMismatchWarning(384, PROPERTY);

        assertFalse(warning.check(embedding(384)).isPresent());
        assertTrue(warning.check(embedding(1536)).isPresent());
    }

    @Test
    void shouldNotWarnForNullEmbedding() {
        DimensionMismatchWarning warning = new DimensionMismatchWarning(384, PROPERTY);

        assertFalse(warning.check((Embedding) null).isPresent());
        assertTrue(warning.check(embedding(1536)).isPresent());
    }

    @Test
    void shouldWarnOnFirstMismatchInBatch() {
        DimensionMismatchWarning warning = new DimensionMismatchWarning(384, PROPERTY);

        Optional<String> message = warning.check(List.of(embedding(384), embedding(1536), embedding(384)));

        assertTrue(message.isPresent());
        assertTrue(message.get().contains("1536"), message.get());
    }

    @Test
    void shouldNotWarnWhenAllBatchEmbeddingsMatch() {
        DimensionMismatchWarning warning = new DimensionMismatchWarning(384, PROPERTY);

        assertFalse(warning.check(List.of(embedding(384), embedding(384))).isPresent());
        assertFalse(warning.check(List.<Embedding> of()).isPresent());
    }

    @Test
    void shouldKeepInstancesIndependent() {
        DimensionMismatchWarning first = new DimensionMismatchWarning(384, PROPERTY);
        DimensionMismatchWarning second = new DimensionMismatchWarning(384, PROPERTY);

        assertTrue(first.check(embedding(1536)).isPresent());
        assertTrue(second.check(embedding(1536)).isPresent());
    }

    private static Embedding embedding(int dimension) {
        return Embedding.from(new float[dimension]);
    }
}
