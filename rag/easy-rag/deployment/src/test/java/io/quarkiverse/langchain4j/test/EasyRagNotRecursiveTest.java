package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.logging.LogRecord;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verify usage of the `quarkus.langchain4j.easy-rag.path-matcher` property.
 */
public class EasyRagNotRecursiveTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset("quarkus.langchain4j.easy-rag.path=src/test/resources/ragdocuments\n" +
                            "quarkus.langchain4j.easy-rag.recursive=false\n"),
                            "application.properties"))
            .setLogRecordPredicate(record -> true)
            .assertLogRecords(EasyRagNotRecursiveTest::verifyLogRecords);

    @Inject
    InMemoryEmbeddingStore<TextSegment> embeddingStore;

    Embedding DUMMY_EMBEDDING = new Embedding(new float[384]);

    private static void verifyLogRecords(List<LogRecord> logRecords) {
        assertThat(logRecords.stream().map(LogRecord::getMessage))
                .contains(
                        "Ingesting documents from path: src/test/resources/ragdocuments, path matcher = glob:**, recursive = false")
                .contains("Ingested 1 files as 1 documents")
                .doesNotContain("Writing embeddings to %s")
                .doesNotContain("Reading embeddings from %s");
    }

    @Test
    public void verifyOnlyTheRootDirectoryIsIngested() {
        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore
                .search(EmbeddingSearchRequest.builder().queryEmbedding(DUMMY_EMBEDDING).maxResults(3).build()).matches();
        assertEquals(1, relevant.size());
        assertTrue(relevant.get(0).embedded().text().contains("Charlie"));
    }

}
