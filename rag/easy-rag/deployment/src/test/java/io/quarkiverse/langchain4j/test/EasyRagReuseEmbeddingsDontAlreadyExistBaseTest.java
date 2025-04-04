package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.logging.LogRecord;

import jakarta.enterprise.inject.spi.CDI;

import org.junit.jupiter.api.Test;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;

abstract class EasyRagReuseEmbeddingsDontAlreadyExistBaseTest {
    protected static void verifyLogRecords(List<LogRecord> logRecords) {
        assertThat(logRecords.stream().map(LogRecord::getMessage))
                .contains(
                        "Ingesting documents from path: src/test/resources/ragdocuments, path matcher = glob:**, recursive = true")
                .contains("Ingested 2 files as 2 documents")
                .contains("Writing embeddings to %s")
                .doesNotContain("Reading embeddings from %s");
    }

    protected abstract Path getEmbeddingsFile();

    @Test
    void verifyThatEmbeddingsFileIsGenerated() {
        assertThat(getEmbeddingsFile())
                .isNotNull()
                .isNotEmptyFile();
    }

    @Test
    public void verifyThatDocumentsAreIngested() {
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
}
