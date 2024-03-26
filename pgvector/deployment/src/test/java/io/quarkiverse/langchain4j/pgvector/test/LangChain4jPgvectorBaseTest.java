package io.quarkiverse.langchain4j.pgvector.test;

import java.sql.SQLException;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import io.quarkiverse.langchain4j.pgvector.PgVectorEmbeddingStore;
import io.quarkus.logging.Log;

abstract class LangChain4jPgvectorBaseTest extends EmbeddingStoreWithFilteringIT {

    @Inject
    PgVectorEmbeddingStore pgvectorEmbeddingStore;

    private static EmbeddingModel embeddingModel;

    /**
     * FIXME: This is a workaround to avoid loading the embedding model in this test class' static initializer,
     * because otherwise we hit
     * java.lang.UnsatisfiedLinkError: Native Library (/path/to/the/library) already loaded in another classloader
     * because the test class is loaded by JUnit and by Quarkus in different class loaders.
     */
    @BeforeAll
    public static void initEmbeddingModel() {
        if (embeddingModel == null) {
            embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
        }
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Override
    protected void clearStore() {
        try {
            pgvectorEmbeddingStore.deleteAll();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return pgvectorEmbeddingStore;
    }

    @Test
    @Disabled("To use locally")
    void testSearch() {
        int nbRows = 20000;
        Log.debugf("Start Ingesting %s embeddings", nbRows);
        IntStream.range(0, nbRows).forEachOrdered(n -> {
            Metadata randomNameMetadata = Metadata.from("name", String.format("name_%s", new Random().nextInt(nbRows)));
            TextSegment textSegment = TextSegment.from("matching", randomNameMetadata);
            Embedding matchingEmbedding = this.embeddingModel().embed(textSegment).content();
            this.embeddingStore().add(matchingEmbedding, textSegment);
        });
        this.awaitUntilPersisted();
        Log.debugf("End Ingesting %s embeddings", nbRows);
        Embedding queryEmbedding = this.embeddingModel().embed("matching").content();
        Log.debug("Query Embedding done, starting query");

        int nbQuery = 50;
        IntStream.range(0, nbQuery).forEachOrdered(n -> {
            String name = String.format("name_%s", new Random().nextInt(nbQuery));
            Log.debugf("Starting query %s", name);
            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder().queryEmbedding(queryEmbedding)
                    .filter(MetadataFilterBuilder.metadataKey("name").isEqualTo(name)).maxResults(100).build();
            List<?> result = this.embeddingStore().search(request).matches();
            Log.debugf("Query done, %s results", result.size());
            Assertions.assertNotNull(result);
        });

    }
}
