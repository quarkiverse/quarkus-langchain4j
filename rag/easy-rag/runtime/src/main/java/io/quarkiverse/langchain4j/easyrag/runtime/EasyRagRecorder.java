package io.quarkiverse.langchain4j.easyrag.runtime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.function.Supplier;

import jakarta.enterprise.inject.Default;

import org.jboss.logging.Logger;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class EasyRagRecorder {

    private static final Logger LOGGER = Logger.getLogger(EasyRagRecorder.class);

    // store the config to be used later by EasyRagManualIngestionTrigger
    public static EasyRagConfig easyRagConfig;

    public void ingest(EasyRagConfig config, BeanContainer beanContainer) {
        easyRagConfig = config;
        if (config.ingestionStrategy() == IngestionStrategy.OFF) {
            LOGGER.info("Skipping document ingestion as per configuration");
            return;
        }
        if (config.ingestionStrategy() == IngestionStrategy.MANUAL) {
            LOGGER.info("Not ingesting documents for now because ingestion strategy is set to MANUAL");
            return;
        }

        EmbeddingStore<TextSegment> embeddingStore = beanContainer.beanInstance(EmbeddingStore.class);
        EmbeddingModel embeddingModel = beanContainer.beanInstance(EmbeddingModel.class);
        new EasyRagIngestor(embeddingModel, embeddingStore, config).ingest();
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
