package io.quarkiverse.langchain4j.mongodb.deployment;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.wildfly.common.Assert;

import com.mongodb.client.MongoClient;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import io.quarkus.test.QuarkusUnitTest;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MongoDBMetadataFilteringTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset(
                            """
                                    quarkus.langchain4j.mongodb.database-name=test
                                    quarkus.langchain4j.mongodb.index-name=vector_index
                                    quarkus.mongodb.devservices.enabled=true
                                    quarkus.langchain4j.mongodb.dimensions=384
                                    quarkus.compose.devservices.files=compose-devservices.yml
                                    quarkus.mongodb.devservices.properties.uuidRepresentation = standard
                                    """),
                            "application.properties")

            );

    @Inject
    EmbeddingStore<TextSegment> embeddingStore;

    @Inject
    MongoClient mongoClient;

    private static EmbeddingModel embeddingModel;

    @BeforeAll
    void ensureEverythingIsReady() {
        embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
        embeddingStore.removeAll();

        await()
                .pollInSameThread()
                .pollInterval(Duration.ofSeconds(2))
                .ignoreExceptions()
                .untilAsserted(() -> {
                    try (var indexCursor = mongoClient.getDatabase("test").getCollection("embeddings")
                            .listSearchIndexes()
                            .cursor()) {

                        if (indexCursor.hasNext()) {
                            var index = indexCursor.next();
                            Assert.assertTrue("READY".equalsIgnoreCase(index.getString("status")));
                        }
                    }
                });
    }

    private void ingest(String text, Map<String, Object> metadata) {
        Document document = Document.from(text, Metadata.from(metadata));
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();
        ingestor.ingest(document);
    }

    @BeforeEach
    public void clearStore() {
        embeddingStore.removeAll();
    }

    @Test
    public void testEqual() {
        ingest("Hello0", Map.of("num1", 0, "num2", 0));
        ingest("Hello1", Map.of("num1", 1, "num2", 1));
        ingest("Hello2", Map.of("num1", 2, "num2", 2));
//        await()
//                .atMost(Duration.ofSeconds(20))
//                .ignoreExceptions()
//                .untilAsserted(() -> {
//                    var result = embeddingStore.search(EmbeddingSearchRequest.builder()
//                            .queryEmbedding(embeddingModel.embed("Hello").content()).build());
//                    assertEquals(3, result.matches().size());
//                        }
//
//                );
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed("Hello").content())
                .filter(new IsEqualTo("num1", 0))
                .build();
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(request).matches();
        assertEquals(1, matches.size());
        assertEquals("Hello0", matches.get(0).embedded().text());
    }

    @Test
    public void testNotEqual() {
        ingest("Hello0", Map.of("num1", 0));
        ingest("Hello1", Map.of("num1", 1));
        ingest("Hello2", Map.of("num1", 2));

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed("Hello").content())
                .filter(new IsNotEqualTo("num1", 0))
                .maxResults(10)
                .build();
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(request).matches();
        assertEquals(2, matches.size());
        Set<String> texts = matches.stream()
                .map(m -> m.embedded().text())
                .collect(Collectors.toSet());
        assertTrue(texts.contains("Hello1"));
        assertTrue(texts.contains("Hello2"));
    }

    @Test
    public void testAndEqual() {
        ingest("Hello0", Map.of("num1", 0, "num2", 0));
        ingest("Hello1", Map.of("num1", 1, "num2", 1));
        ingest("Hello22", Map.of("num1", 2, "num2", 2));
        ingest("Hello22x", Map.of("num1", 2, "num2", 2));
        ingest("Hello23", Map.of("num1", 2, "num2", 3));

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed("Hello").content())
                .filter(new And(new IsEqualTo("num1", 2), new IsEqualTo("num2", 2)))
                .maxResults(10)
                .build();
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(request).matches();
        assertEquals(2, matches.size());
        Set<String> retrievedTextSegments = matches.stream()
                .map(textSegmentEmbeddingMatch -> textSegmentEmbeddingMatch.embedded().text())
                .collect(Collectors.toSet());
        assertTrue(retrievedTextSegments.contains("Hello22"));
        assertTrue(retrievedTextSegments.contains("Hello22x"));
    }

    @Test
    public void testGreaterThan() {
        ingest("Hello0", Map.of("score", 0));
        ingest("Hello1", Map.of("score", 1));
        ingest("Hello2", Map.of("score", 2));
        ingest("Hello3", Map.of("score", 3));

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed("Hello").content())
                .filter(new IsGreaterThan("score", 1))
                .maxResults(10)
                .build();
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(request).matches();
        assertEquals(2, matches.size());
        Set<String> texts = matches.stream()
                .map(m -> m.embedded().text())
                .collect(Collectors.toSet());
        assertTrue(texts.contains("Hello2"));
        assertTrue(texts.contains("Hello3"));
    }

    @Test
    public void testGreaterThanOrEqual() {
        ingest("Hello0", Map.of("score", 0));
        ingest("Hello1", Map.of("score", 1));
        ingest("Hello2", Map.of("score", 2));
        ingest("Hello3", Map.of("score", 3));

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed("Hello").content())
                .filter(new IsGreaterThanOrEqualTo("score", 2))
                .maxResults(10)
                .build();
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(request).matches();
        assertEquals(2, matches.size());
        Set<String> texts = matches.stream()
                .map(m -> m.embedded().text())
                .collect(Collectors.toSet());
        assertTrue(texts.contains("Hello2"));
        assertTrue(texts.contains("Hello3"));
    }

    @Test
    public void testLessThan() {
        ingest("Hello0", Map.of("score", 0));
        ingest("Hello1", Map.of("score", 1));
        ingest("Hello2", Map.of("score", 2));
        ingest("Hello3", Map.of("score", 3));

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed("Hello").content())
                .filter(new IsLessThan("score", 2))
                .maxResults(10)
                .build();
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(request).matches();
        assertEquals(2, matches.size());
        Set<String> texts = matches.stream()
                .map(m -> m.embedded().text())
                .collect(Collectors.toSet());
        assertTrue(texts.contains("Hello0"));
        assertTrue(texts.contains("Hello1"));
    }

    @Test
    public void testLessThanOrEqual() {
        ingest("Hello0", Map.of("score", 0));
        ingest("Hello1", Map.of("score", 1));
        ingest("Hello2", Map.of("score", 2));
        ingest("Hello3", Map.of("score", 3));

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed("Hello").content())
                .filter(new IsLessThanOrEqualTo("score", 1))
                .maxResults(10)
                .build();
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(request).matches();
        assertEquals(2, matches.size());
        Set<String> texts = matches.stream()
                .map(m -> m.embedded().text())
                .collect(Collectors.toSet());
        assertTrue(texts.contains("Hello0"));
        assertTrue(texts.contains("Hello1"));
    }

    @Test
    public void testIsIn() {
        ingest("Hello0", Map.of("category", 0));
        ingest("Hello1", Map.of("category", 1));
        ingest("Hello2", Map.of("category", 2));
        ingest("Hello3", Map.of("category", 3));
        ingest("Hello4", Map.of("category", 4));

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed("Hello").content())
                .filter(new IsIn("category", Arrays.asList(1, 3, 4)))
                .maxResults(10)
                .build();
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(request).matches();
        assertEquals(3, matches.size());
        Set<String> texts = matches.stream()
                .map(m -> m.embedded().text())
                .collect(Collectors.toSet());
        assertTrue(texts.contains("Hello1"));
        assertTrue(texts.contains("Hello3"));
        assertTrue(texts.contains("Hello4"));
    }

    @Test
    public void testIsNotIn() {
        ingest("Hello0", Map.of("category", 0));
        ingest("Hello1", Map.of("category", 1));
        ingest("Hello2", Map.of("category", 2));
        ingest("Hello3", Map.of("category", 3));

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed("Hello").content())
                .filter(new IsNotIn("category", Arrays.asList(0, 1)))
                .maxResults(10)
                .build();
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(request).matches();
        assertEquals(2, matches.size());
        Set<String> texts = matches.stream()
                .map(m -> m.embedded().text())
                .collect(Collectors.toSet());
        assertTrue(texts.contains("Hello2"));
        assertTrue(texts.contains("Hello3"));
    }

    @Test
    public void testContainsString() {
        ingest("Hello world", Map.of("tag", "important"));
        ingest("Hello there", Map.of("tag", "normal"));
        ingest("Hello friend", Map.of("tag", "important_message"));
        ingest("Hello stranger", Map.of("tag", "casual"));

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed("Hello").content())
                .filter(new ContainsString("tag", "important"))
                .maxResults(10)
                .build();
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(request).matches();
        assertEquals(2, matches.size());
        Set<String> texts = matches.stream()
                .map(m -> m.embedded().text())
                .collect(Collectors.toSet());
        assertTrue(texts.contains("Hello world"));
        assertTrue(texts.contains("Hello friend"));
    }

    @Test
    public void testComplexFilter() {
        ingest("Doc1", Map.of("score", 5, "category", 1, "tag", "important"));
        ingest("Doc2", Map.of("score", 7, "category", 2, "tag", "normal"));
        ingest("Doc3", Map.of("score", 8, "category", 1, "tag", "important"));
        ingest("Doc4", Map.of("score", 3, "category", 1, "tag", "casual"));
        ingest("Doc5", Map.of("score", 9, "category", 3, "tag", "important"));

        // Filter: (score >= 5 AND category = 1) AND tag contains "important"
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed("Doc").content())
                .filter(new And(
                        new And(
                                new IsGreaterThanOrEqualTo("score", 5),
                                new IsEqualTo("category", 1)),
                        new ContainsString("tag", "important")))
                .maxResults(10)
                .build();
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(request).matches();
        assertEquals(2, matches.size());
        Set<String> texts = matches.stream()
                .map(m -> m.embedded().text())
                .collect(Collectors.toSet());
        assertTrue(texts.contains("Doc1"));
        assertTrue(texts.contains("Doc3"));
    }
}
