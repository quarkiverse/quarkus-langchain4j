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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import io.quarkus.test.QuarkusUnitTest;

class EasyRagReuseEmbeddingsOnClasspathAlreadyExistTest {
    private static final String EMBEDDING_FILE_NAME = "embeddings.json";
    private static final Path EMBEDDINGS_DIR = Path.of("src", "test", "resources", "embeddings");

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(Path.of("target", "test-classes", "ragdocuments").toFile(), "ragdocuments")
                    .addAsResource(new StringAsset("""
                            quarkus.langchain4j.easy-rag.path=ragdocuments
                            quarkus.langchain4j.easy-rag.path-type=CLASSPATH
                            quarkus.langchain4j.easy-rag.reuse-embeddings.enabled=true
                            quarkus.langchain4j.easy-rag.reuse-embeddings.file=%s
                            """.formatted(embeddingsFile())),
                            "application.properties"))
            .setLogRecordPredicate(record -> true)
            .assertLogRecords(EasyRagReuseEmbeddingsOnClasspathAlreadyExistTest::verifyLogRecords);

    private static Path embeddingsFile() {
        return EMBEDDINGS_DIR.resolve(EMBEDDING_FILE_NAME).toAbsolutePath();
    }

    private static void verifyLogRecords(List<LogRecord> logRecords) {
        assertThat(logRecords.stream().map(LogRecord::getMessage))
                .doesNotContain(
                        "Ingesting documents from classpath: ragdocuments, path matcher = glob:**, recursive = true")
                .doesNotContain("Ingested 2 files as 2 documents")
                .contains("Reading embeddings from %s")
                .doesNotContain("Writing embeddings to %s");
    }

    @Test
    public void verifyThatEmbeddingsAreIngested() {
        EmbeddingModel embeddingModel = CDI.current().select(EmbeddingModel.class).get();
        EmbeddingStore embeddingStore = CDI.current().select(EmbeddingStore.class).get();
        Embedding question = embeddingModel.embed("When was Charlie born?").content();
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore
                .search(EmbeddingSearchRequest.builder().queryEmbedding(question).maxResults(1).build()).matches();
        assertTrue(matches.get(0).embedded().text().contains("2005"));

        question = embeddingModel.embed("When was David born?").content();
        matches = embeddingStore.search(EmbeddingSearchRequest.builder().queryEmbedding(question).maxResults(1).build())
                .matches();
        assertTrue(matches.get(0).embedded().text().contains("2003"));
    }

    @Test
    @Disabled("Only when changing the embeddings model")
    public void regenerateEmbeddingStoreOnFileSystem() {
        EmbeddingModel embeddingModel = CDI.current().select(EmbeddingModel.class).get();
        EmbeddingStore embeddingStore = CDI.current().select(EmbeddingStore.class).get();
        TextSegment segment = TextSegment.textSegment("Charlie was born in 2005.");
        embeddingStore.add(embeddingModel.embed(segment).content(), segment);

        segment = TextSegment.textSegment("David was born in 2003.");
        embeddingStore.add(embeddingModel.embed(segment).content(), segment);

        assertThat(embeddingStore).isInstanceOf(InMemoryEmbeddingStore.class);

        ((InMemoryEmbeddingStore) embeddingStore).serializeToFile("src/test/resources/embeddings/embeddings.json");
    }
}
