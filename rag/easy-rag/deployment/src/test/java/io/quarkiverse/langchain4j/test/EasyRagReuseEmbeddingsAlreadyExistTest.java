package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.logging.LogRecord;

import jakarta.enterprise.inject.spi.CDI;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkus.test.QuarkusUnitTest;

class EasyRagReuseEmbeddingsAlreadyExistTest {
    private static final String EMBEDDING_FILE_NAME = "embeddings.json";
    private static final Path EMBEDDINGS_DIR = Path.of("src", "test", "resources", "embeddings");

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset("""
                            quarkus.langchain4j.easy-rag.path=src/test/resources/ragdocuments
                            quarkus.langchain4j.easy-rag.reuse-embeddings.enabled=true
                            quarkus.langchain4j.easy-rag.reuse-embeddings.file=%s
                            """.formatted(embeddingsFile())),
                            "application.properties"))
            .setLogRecordPredicate(record -> true)
            .assertLogRecords(EasyRagReuseEmbeddingsAlreadyExistTest::verifyLogRecords);

    private static Path embeddingsFile() {
        return EMBEDDINGS_DIR.resolve(EMBEDDING_FILE_NAME).toAbsolutePath();
    }

    private static void verifyLogRecords(List<LogRecord> logRecords) {
        assertThat(logRecords.stream().map(LogRecord::getMessage))
                .doesNotContain(
                        "Ingesting documents from path: src/test/resources/ragdocuments, path matcher = glob:**, recursive = true")
                .doesNotContain("Ingested 2 files as 2 documents")
                .contains("Reading embeddings from %s")
                .doesNotContain("Writing embeddings to %s");
    }

    @Test
    public void verifyThatEmbeddingsAreIngested() {
        EmbeddingModel embeddingModel = CDI.current().select(EmbeddingModel.class).get();
        EmbeddingStore embeddingStore = CDI.current().select(EmbeddingStore.class).get();
        Embedding question = embeddingModel.embed("When was Charlie born?").content();
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.findRelevant(question, 1);
        assertTrue(matches.get(0).embedded().text().contains("2005"));

        question = embeddingModel.embed("When was David born?").content();
        matches = embeddingStore.findRelevant(question, 1);
        assertTrue(matches.get(0).embedded().text().contains("2003"));
    }
}
