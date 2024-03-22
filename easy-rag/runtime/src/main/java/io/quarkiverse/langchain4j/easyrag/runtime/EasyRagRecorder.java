package io.quarkiverse.langchain4j.easyrag.runtime;

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
        EmbeddingStore<TextSegment> embeddingStore = beanContainer.beanInstance(EmbeddingStore.class);
        EmbeddingModel embeddingModel = beanContainer.beanInstance(EmbeddingModel.class);
        LOGGER.info("Ingesting documents from path: " + config.path());
        List<Document> documents = FileSystemDocumentLoader.loadDocuments(config.path());
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

    public Supplier<InMemoryEmbeddingStore> inMemoryEmbeddingStoreSupplier() {
        return new Supplier<>() {
            @Override
            public InMemoryEmbeddingStore get() {
                return new InMemoryEmbeddingStore<>();
            }
        };

    }

    public Function<SyntheticCreationalContext<RetrievalAugmentor>, RetrievalAugmentor> easyRetrievalAugmentorFunction(
            EasyRagConfig config) {
        return new Function<>() {
            @Override
            public RetrievalAugmentor apply(SyntheticCreationalContext<RetrievalAugmentor> context) {
                EmbeddingModel model = context.getInjectedReference(EmbeddingModel.class, new Default.Literal());
                EmbeddingStore<TextSegment> store = context.getInjectedReference(EmbeddingStore.class, new Default.Literal());
                return new EasyRetrievalAugmentor(config.maxResults(), model, store);
            }
        };
    }
}
