package io.quarkiverse.langchain4j.sample.chatbot;

import static dev.langchain4j.data.document.splitter.DocumentSplitters.recursive;

import java.io.File;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import io.quarkiverse.langchain4j.redis.RedisEmbeddingStore;
import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
public class IngestorExample {

    /**
     * The embedding store (the database).
     * The bean is provided by the quarkus-langchain4j-redis extension.
     */
    @Inject
    RedisEmbeddingStore store;

    /**
     * The embedding model (how the vector of a document is computed).
     * The bean is provided by the LLM (like openai) extension.
     */
    @Inject
    EmbeddingModel embeddingModel;

    public void ingest(@Observes StartupEvent event) {
        System.out.printf("Ingesting documents...%n");
        List<Document> documents = FileSystemDocumentLoader.loadDocuments(new File("src/main/resources/catalog").toPath(),
                new TextDocumentParser());
        var ingestor = EmbeddingStoreIngestor.builder()
                .embeddingStore(store)
                .embeddingModel(embeddingModel)
                .documentSplitter(recursive(500, 0))
                .build();
        ingestor.ingest(documents);
        System.out.printf("Ingested %d documents.%n", documents.size());
    }
}
