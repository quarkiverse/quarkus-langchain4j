package io.quarkiverse.langchain4j.easyrag.runtime;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;

import org.jboss.logging.Logger;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.ClassPathDocumentLoader;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.HuggingFaceTokenCountEstimator;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

public class EasyRagIngestor {
    private static final Logger LOGGER = Logger.getLogger(EasyRagIngestor.class);

    private EmbeddingModel embeddingModel;
    private EmbeddingStore<TextSegment> embeddingStore;
    private EasyRagConfig config;

    public EasyRagIngestor(EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore,
            EasyRagConfig config) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.config = config;
    }

    public void ingest() {
        if (config.reuseEmbeddings().enabled() && (embeddingStore instanceof InMemoryEmbeddingStore<TextSegment>)) {
            Path embeddingsFile = Path.of(config.reuseEmbeddings().file()).toAbsolutePath();

            // If the embeddings file already exists it would have been ingested
            // when the InMemoryEmbeddingStore bean was created
            // See the inMemoryEmbeddingStoreSupplier method
            if (!Files.exists(embeddingsFile)) {
                // embeddingsFile doesn't exist, so ingest the documents and then write out the results
                try {
                    Files.createDirectories(embeddingsFile.getParent());
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }

                ingestDocumentsFromFilesystem(config, embeddingStore, embeddingModel);
                LOGGER.infof("Writing embeddings to %s", embeddingsFile);
                ((InMemoryEmbeddingStore<TextSegment>) embeddingStore).serializeToFile(embeddingsFile);
            } else {
                // This is here because in the case where the file exists, the EmbeddingStore will be
                // lazily initialized upon first use. We want it eagerly initialized.
                // We need to call a method on the bean instance to eagerly initialize it
                // https://github.com/quarkusio/quarkus/issues/41159 may make this less "hacky" in the future
                embeddingStore.toString();
            }
        } else {
            ingestDocumentsFromFilesystem(config, embeddingStore, embeddingModel);
        }
    }

    private List<Document> getDocuments(EasyRagConfig config) {
        PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher(config.pathMatcher());
        boolean recursive = config.recursive();

        return switch (config.pathType()) {
            case CLASSPATH -> recursive
                    ? ClassPathDocumentLoader.loadDocumentsRecursively(config.path(), pathMatcher)
                    : ClassPathDocumentLoader.loadDocuments(config.path(), pathMatcher);

            case FILESYSTEM -> recursive
                    ? FileSystemDocumentLoader.loadDocumentsRecursively(config.path(), pathMatcher)
                    : FileSystemDocumentLoader.loadDocuments(config.path(), pathMatcher);
        };
    }

    private void ingestDocumentsFromFilesystem(EasyRagConfig config, EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel) {

        var msg = "Ingesting documents from %s: %s, path matcher = %s, recursive = %s".formatted(
                config.pathType().name().toLowerCase(),
                config.path(),
                config.pathMatcher(),
                config.recursive());
        LOGGER.info(msg);

        List<Document> documents = getDocuments(config);
        DocumentSplitter documentSplitter = DocumentSplitters.recursive(config.maxSegmentSize(),
                config.maxOverlapSize(), new HuggingFaceTokenCountEstimator());

        List<Document> splitDocuments = documentSplitter
                .splitAll(documents)
                .stream()
                .map(split -> Document.document(split.text()))
                .toList();

        EmbeddingStoreIngestor.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build()
                .ingest(splitDocuments);

        LOGGER.info("Ingested " + documents.size() + " files as " + splitDocuments.size() + " documents");
    }
}
