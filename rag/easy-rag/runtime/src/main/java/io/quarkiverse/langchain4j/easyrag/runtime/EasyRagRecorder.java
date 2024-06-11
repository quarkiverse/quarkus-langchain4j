package io.quarkiverse.langchain4j.easyrag.runtime;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import jakarta.enterprise.inject.Default;

import org.jboss.logging.Logger;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.HuggingFaceTokenizer;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class EasyRagRecorder {

    private static final Logger LOGGER = Logger.getLogger(EasyRagRecorder.class);

    public void ingest(EasyRagConfig config, BeanContainer beanContainer) {
        if (config.ingestionStrategy() == IngestionStrategy.OFF) {
            LOGGER.info("Skipping document ingestion as per configuration");
            return;
        }

        EmbeddingStore<TextSegment> embeddingStore = beanContainer.beanInstance(EmbeddingStore.class);
        EmbeddingModel embeddingModel = beanContainer.beanInstance(EmbeddingModel.class);

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

    private void ingestDocumentsFromFilesystem(EasyRagConfig config, EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel) {
        PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher(config.pathMatcher());
        LOGGER.info("Ingesting documents from path: " + config.path() +
                ", path matcher = " + config.pathMatcher() + ", recursive = " + config.recursive());

        List<Document> documents = config.recursive()
                ? FileSystemDocumentLoader.loadDocumentsRecursively(config.path(), pathMatcher)
                : FileSystemDocumentLoader.loadDocuments(config.path(), pathMatcher);

        DocumentSplitter documentSplitter = DocumentSplitters.recursive(config.maxSegmentSize(),
                config.maxOverlapSize(), new HuggingFaceTokenizer());

        List<Document> splitDocuments = documentSplitter
                .splitAll(documents)
                .stream()
                .map(split -> new Document(split.text()))
                .toList();

        EmbeddingStoreIngestor.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build()
                .ingest(splitDocuments);

        LOGGER.info("Ingested " + documents.size() + " files as " + splitDocuments.size() + " documents");
    }

    public Supplier<InMemoryEmbeddingStore<TextSegment>> inMemoryEmbeddingStoreSupplier(EasyRagConfig config) {
        return new Supplier<>() {
            @Override
            public InMemoryEmbeddingStore<TextSegment> get() {
                if ((config.ingestionStrategy() == IngestionStrategy.ON) && config.reuseEmbeddings().enabled()) {
                    // Want to reuse existing embeddings
                    Path embeddingsFile = Path.of(config.reuseEmbeddings().file()).toAbsolutePath();

                    // If the file exists then read it and populate
                    if (Files.isRegularFile(embeddingsFile)) {
                        LOGGER.infof("Reading embeddings from %s", embeddingsFile);
                        return InMemoryEmbeddingStore.fromFile(embeddingsFile);
                    }
                }

                // Otherwise just return an empty store
                return new InMemoryEmbeddingStore<>();
            }
        };

    }

    public Function<SyntheticCreationalContext<RetrievalAugmentor>, RetrievalAugmentor> easyRetrievalAugmentorFunction(
            EasyRagConfig config) {
        return new Function<>() {
            @Override
            public RetrievalAugmentor apply(SyntheticCreationalContext<RetrievalAugmentor> context) {
                EmbeddingModel model = context.getInjectedReference(EmbeddingModel.class, Default.Literal.INSTANCE);
                EmbeddingStore<TextSegment> store = context.getInjectedReference(EmbeddingStore.class,
                        Default.Literal.INSTANCE);
                return new EasyRetrievalAugmentor(config.maxResults(), model, store);
            }
        };
    }
}
