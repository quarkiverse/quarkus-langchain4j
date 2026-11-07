package io.quarkiverse.langchain4j.infinispan.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThan;
import dev.langchain4j.store.embedding.filter.comparison.IsIn;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotIn;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import io.quarkiverse.langchain4j.infinispan.InfinispanEmbeddingStore;
import io.quarkus.test.QuarkusUnitTest;

// Cannot extend EmbeddingStoreWithFilteringIT because it contains tests parametrized through
// @MethodSource, which is not supported by the quarkus-junit5-internal testing framework.
public class InfinispanMetadataFilteringTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset("quarkus.langchain4j.infinispan.dimension=384"),
                            "application.properties"));

    @Inject
    InfinispanEmbeddingStore embeddingStore;

    static EmbeddingModel embeddingModel;

    @BeforeAll
    public static void initEmbeddingModel() {
        embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
    }

    @AfterEach
    public void clearStore() {
        embeddingStore.removeAll();
    }

    private void ingest(String text, Map<String, Object> metadata) {
        Document document = Document.from(text, Metadata.from(metadata));
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();
        ingestor.ingest(document);
    }

    private List<EmbeddingMatch<TextSegment>> search(dev.langchain4j.store.embedding.filter.Filter filter) {
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed("Hello").content())
                .filter(filter)
                .build();
        return embeddingStore.search(request).matches();
    }

    @Test
    public void testEqual() {
        ingest("Hello0", Map.of("num1", 0, "num2", 0));
        ingest("Hello1", Map.of("num1", 1, "num2", 1));
        ingest("Hello2", Map.of("num1", 2, "num2", 2));

        List<EmbeddingMatch<TextSegment>> matches = search(new IsEqualTo("num1", 0));
        assertEquals(1, matches.size());
        assertEquals("Hello0", matches.get(0).embedded().text());
    }

    @Test
    public void testEqualString() {
        ingest("Hello0", Map.of("type", "a"));
        ingest("Hello1", Map.of("type", "b"));
        ingest("Hello2", Map.of("type", "a"));

        List<EmbeddingMatch<TextSegment>> matches = search(new IsEqualTo("type", "a"));
        assertEquals(2, matches.size());
    }

    @Test
    public void testNotEqual() {
        ingest("Hello0", Map.of("num1", 0));
        ingest("Hello1", Map.of("num1", 1));
        ingest("Hello2", Map.of("num1", 2));

        List<EmbeddingMatch<TextSegment>> matches = search(new IsNotEqualTo("num1", 0));
        assertEquals(2, matches.size());
    }

    @Test
    public void testGreaterThan() {
        ingest("Hello0", Map.of("num1", 0));
        ingest("Hello1", Map.of("num1", 1));
        ingest("Hello2", Map.of("num1", 2));

        List<EmbeddingMatch<TextSegment>> matches = search(new IsGreaterThan("num1", 0));
        assertEquals(2, matches.size());
    }

    @Test
    public void testLessThanOrEqual() {
        ingest("Hello0", Map.of("num1", 0));
        ingest("Hello1", Map.of("num1", 1));
        ingest("Hello2", Map.of("num1", 2));

        List<EmbeddingMatch<TextSegment>> matches = search(new IsLessThanOrEqualTo("num1", 1));
        assertEquals(2, matches.size());
    }

    @Test
    public void testIn() {
        ingest("Hello0", Map.of("type", "a"));
        ingest("Hello1", Map.of("type", "b"));
        ingest("Hello2", Map.of("type", "c"));

        List<EmbeddingMatch<TextSegment>> matches = search(new IsIn("type", List.of("a", "b")));
        assertEquals(2, matches.size());
    }

    @Test
    public void testNotIn() {
        ingest("Hello0", Map.of("type", "a"));
        ingest("Hello1", Map.of("type", "b"));
        ingest("Hello2", Map.of("type", "c"));

        List<EmbeddingMatch<TextSegment>> matches = search(new IsNotIn("type", List.of("a", "b")));
        assertEquals(1, matches.size());
        assertEquals("Hello2", matches.get(0).embedded().text());
    }

    @Test
    public void testAndEqual() {
        ingest("Hello0", Map.of("num1", 0, "num2", 0));
        ingest("Hello1", Map.of("num1", 1, "num2", 1));
        ingest("Hello22", Map.of("num1", 2, "num2", 2));
        ingest("Hello22x", Map.of("num1", 2, "num2", 2));
        ingest("Hello23", Map.of("num1", 2, "num2", 3));

        List<EmbeddingMatch<TextSegment>> matches = search(
                new And(new IsEqualTo("num1", 2), new IsEqualTo("num2", 2)));
        assertEquals(2, matches.size());
        Set<String> texts = matches.stream()
                .map(m -> m.embedded().text())
                .collect(Collectors.toSet());
        Assertions.assertTrue(texts.contains("Hello22"));
        Assertions.assertTrue(texts.contains("Hello22x"));
    }

    @Test
    public void testOr() {
        ingest("Hello0", Map.of("num1", 0));
        ingest("Hello1", Map.of("num1", 1));
        ingest("Hello2", Map.of("num1", 2));

        List<EmbeddingMatch<TextSegment>> matches = search(
                new Or(new IsEqualTo("num1", 0), new IsEqualTo("num1", 2)));
        assertEquals(2, matches.size());
    }

    @Test
    public void testNot() {
        ingest("Hello0", Map.of("num1", 0));
        ingest("Hello1", Map.of("num1", 1));
        ingest("Hello2", Map.of("num1", 2));

        List<EmbeddingMatch<TextSegment>> matches = search(new Not(new IsEqualTo("num1", 1)));
        assertEquals(2, matches.size());
    }

    @Test
    public void testRemoveAllByFilter() {
        ingest("Hello0", Map.of("type", "a"));
        ingest("Hello1", Map.of("type", "a"));
        ingest("Hello2", Map.of("type", "b"));

        embeddingStore.removeAll(new IsEqualTo("type", "a"));

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed("Hello").content())
                .maxResults(100)
                .build();
        List<EmbeddingMatch<TextSegment>> remaining = embeddingStore.search(request).matches();
        assertEquals(1, remaining.size());
        assertEquals("Hello2", remaining.get(0).embedded().text());
    }
}
