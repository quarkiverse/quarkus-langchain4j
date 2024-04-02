package io.quarkiverse.langchain4j.pgvector.test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import javax.sql.DataSource;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Assertions;
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
import io.quarkus.logging.Log;

abstract class LangChain4jPgVectorBaseTest extends EmbeddingStoreWithFilteringIT {
    private static final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
    @Inject
    protected EmbeddingStore<TextSegment> pgvectorEmbeddingStore;
    @Inject
    DataSource dataSource;

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Override
    protected void clearStore() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(String.format("TRUNCATE TABLE %s", "embeddings"));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return pgvectorEmbeddingStore;
    }

    /**
     * Just for information, not real benchmark.
     * JSONTest: Ingesting time 50849 ms. Query average 10 ms
     * JSONBTest: Ingesting time 56035 ms. Query average 6 ms.
     * JSONBMultiIndexTest: Ingesting time 47344 ms. Query average 6 ms.
     * ColumnsTest: Ingesting time 49752 ms. Query average 3 ms.
     */
    @Test
    @Disabled("To use locally")
    void testSearch() {
        int nbRows = 20000;
        long startTime = System.currentTimeMillis();
        Log.debugf("Start Ingesting %s embeddings", nbRows);
        IntStream.range(0, nbRows).forEachOrdered(n -> {
            Metadata randomNameMetadata = Metadata.from("name", String.format("name_%s", new Random().nextInt(nbRows)));
            TextSegment textSegment = TextSegment.from("matching", randomNameMetadata);
            Embedding matchingEmbedding = this.embeddingModel().embed(textSegment).content();
            this.embeddingStore().add(matchingEmbedding, textSegment);
        });
        this.awaitUntilPersisted();
        long totalIngestTime = System.currentTimeMillis() - startTime;
        Log.debugf("End Ingesting %s embeddings in %d ms.", nbRows, totalIngestTime);
        Embedding queryEmbedding = this.embeddingModel().embed("matching").content();
        AtomicLong totalQueryTime = new AtomicLong();
        int nbQuery = 60;
        int nbWarmUp = 10;
        IntStream.range(0, nbQuery).forEachOrdered(n -> {
            String name = String.format("name_%s", new Random().nextInt(nbQuery));
            long startTime2 = System.currentTimeMillis();
            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder().queryEmbedding(queryEmbedding)
                    .filter(MetadataFilterBuilder.metadataKey("name").isEqualTo(name)).maxResults(100).build();
            List<?> result = this.embeddingStore().search(request).matches();
            long queryTime = System.currentTimeMillis() - startTime2;
            if (n >= nbWarmUp) {
                totalQueryTime.addAndGet(queryTime);
            }
            Log.debugf("Query %s done, %s results in %d ms.", name, result.size(), queryTime);
            Assertions.assertNotNull(result);
        });
        Log.debugf("Ingesting time %d ms. Query average %d ms.", totalIngestTime,
                totalQueryTime.get() / (nbQuery - nbWarmUp));
    }
}
