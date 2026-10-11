package io.quarkiverse.langchain4j.runtime.embeddingstore;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.langchain4j.data.embedding.Embedding;

/**
 * Emits a warning when the dimension of an embedding does not match the dimension the embedding store was
 * configured with. The warning is emitted at most once per instance, so it does not flood the logs when many
 * embeddings are added. One instance is meant to be held per embedding store.
 */
public final class DimensionMismatchWarning {

    private final int expectedDimension;
    private final String configProperty;
    private final AtomicBoolean warned = new AtomicBoolean(false);

    public DimensionMismatchWarning(int expectedDimension, String configProperty) {
        this.expectedDimension = expectedDimension;
        this.configProperty = configProperty;
    }

    /**
     * @return the warning message when the embedding dimension does not match and no warning was emitted yet,
     *         an empty {@link Optional} otherwise
     */
    public Optional<String> check(Embedding embedding) {
        if (embedding == null || embedding.dimension() == expectedDimension) {
            return Optional.empty();
        }
        if (!warned.compareAndSet(false, true)) {
            return Optional.empty();
        }
        return Optional.of(("Embedding dimension (%d) does not match the configured dimension (%d), "
                + "so the embeddings might not be found during a search. "
                + "Please check the '%s' property. This warning will be shown only once.")
                .formatted(embedding.dimension(), expectedDimension, configProperty));
    }

    /**
     * Checks each embedding in order and returns the warning message for the first mismatch, if any.
     */
    public Optional<String> check(List<Embedding> embeddings) {
        if (embeddings != null) {
            for (Embedding embedding : embeddings) {
                Optional<String> message = check(embedding);
                if (message.isPresent()) {
                    return message;
                }
            }
        }
        return Optional.empty();
    }
}
