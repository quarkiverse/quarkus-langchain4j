package io.quarkiverse.langchain4j.milvus.deployment;

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
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import io.quarkus.test.QuarkusUnitTest;

// temporary solution until we figure out how to extend EmbeddingStoreWithFilteringIT
// (which contains tests parametrized through @MethodSource, which is not supported
// by the quarkus-junit5-internal testing framework)
public class MilvusMetadataFilteringTest {

    public static final String COLLECTION_NAME = "test_embeddings";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset(
                            "quarkus.langchain4j.milvus.collection-name=" + COLLECTION_NAME + "\n" +
                                    "quarkus.langchain4j.milvus.devservices.port=19530\n" +
                                    "quarkus.langchain4j.milvus.consistency-level=STRONG\n" +
                                    "quarkus.langchain4j.milvus.dimension=384"),
                            "application.properties"));

    @Inject
    MilvusEmbeddingStore embeddingStore;

    @BeforeAll
    public static void initEmbeddingModel() {
        embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
    }

    static EmbeddingModel embeddingModel;

    private void ingest(String text, Map<String, Object> metadata) {
        Document document = Document.from(text, Metadata.from(metadata));
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();
        ingestor.ingest(document);
    }

    @AfterEach
    public void clearStore() {
        embeddingStore.removeAll();
    }

    @Test
    public void testEqual() {
        ingest("Hello0", Map.of("num1", 0, "num2", 0));
        ingest("Hello1", Map.of("num1", 1, "num2", 1));
        ingest("Hello2", Map.of("num1", 2, "num2", 2));

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed("Hello").content())
                .filter(new IsEqualTo("num1", 0))
                .build();
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(request).matches();
        Assertions.assertEquals(1, matches.size());
        Assertions.assertEquals("Hello0", matches.get(0).embedded().text());
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
                .build();
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(request).matches();
        Assertions.assertEquals(2, matches.size());
        Set<String> retrievedTextSegments = matches.stream()
                .map(textSegmentEmbeddingMatch -> textSegmentEmbeddingMatch.embedded().text())
                .collect(Collectors.toSet());
        Assertions.assertTrue(retrievedTextSegments.contains("Hello22"));
        Assertions.assertTrue(retrievedTextSegments.contains("Hello22x"));
    }

}
