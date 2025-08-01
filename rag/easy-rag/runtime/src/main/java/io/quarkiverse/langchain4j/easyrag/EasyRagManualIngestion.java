package io.quarkiverse.langchain4j.easyrag;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkiverse.langchain4j.easyrag.runtime.EasyRagConfig;
import io.quarkiverse.langchain4j.easyrag.runtime.EasyRagIngestor;
import io.quarkiverse.langchain4j.easyrag.runtime.IngestionStrategy;

@ApplicationScoped
public class EasyRagManualIngestion {
    @Inject
    EasyRagConfig config;

    public void ingest() {
        if (config.ingestionStrategy() != IngestionStrategy.MANUAL) {
            throw new IllegalStateException("Manual ingestion trigger called when " +
                    "`quarkus.langchain4j.easy-rag.ingestion-strategy` is not MANUAL");
        }
        EmbeddingModel embeddingModel = CDI.current().select(EmbeddingModel.class).get();
        EmbeddingStore<TextSegment> embeddingStore = CDI.current().select(EmbeddingStore.class).get();
        EasyRagIngestor ingestor = new EasyRagIngestor(embeddingModel, embeddingStore, config);
        ingestor.ingest();
    }
}
