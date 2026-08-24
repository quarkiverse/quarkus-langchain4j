package org.acme.example;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import org.apache.arrow.memory.RootAllocator;
import org.lance.namespace.LanceNamespace;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.lancedb.LanceDbEmbeddingStore;
import dev.langchain4j.store.embedding.lancedb.LanceDbEmbeddingStore.DistanceType;

/**
 * Test-only producer that backs the integration test with a local, file-system based LanceDB namespace ("dir" mode),
 * following the upstream {@code LanceDbEmbeddingStoreIT} pattern. This removes the need for LanceDB Cloud credentials
 * in CI.
 * <p>
 * The config-driven store produced by the extension is disabled for this test via
 * {@code quarkus.langchain4j.lancedb.default-store-enabled=false}, so this producer is the sole {@link EmbeddingStore}.
 */
@ApplicationScoped
public class LanceDbTestStoreProducer {

    // AllMiniLmL6V2 produces 384-dimensional embeddings.
    private static final int DIMENSION = 384;

    @Produces
    @Singleton
    public EmbeddingStore<TextSegment> lanceDbEmbeddingStore() {
        try {
            String root = Files.createTempDirectory("lancedb-it").toString();
            LanceNamespace namespace = LanceNamespace.connect("dir", Map.of("root", root), new RootAllocator());
            return new LanceDbEmbeddingStore(namespace, "embeddings", DIMENSION, DistanceType.cosine);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create temporary LanceDB directory for integration test", e);
        }
    }
}
