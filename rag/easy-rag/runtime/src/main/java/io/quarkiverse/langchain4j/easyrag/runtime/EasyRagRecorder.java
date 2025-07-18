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
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class EasyRagRecorder {
    private static final Logger LOGGER = Logger.getLogger(EasyRagRecorder.class);

    private final RuntimeValue<EasyRagConfig> runtimeConfig;

    public EasyRagRecorder(RuntimeValue<EasyRagConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public void ingest(BeanContainer beanContainer) {
        if (runtimeConfig.getValue().ingestionStrategy() == IngestionStrategy.OFF) {
            LOGGER.info("Skipping document ingestion as per configuration");
            return;
        }
        if (runtimeConfig.getValue().ingestionStrategy() == IngestionStrategy.MANUAL) {
            LOGGER.info("Not ingesting documents for now because ingestion strategy is set to MANUAL");
            return;
        }

        EmbeddingStore<TextSegment> embeddingStore = beanContainer.beanInstance(EmbeddingStore.class);
        EmbeddingModel embeddingModel = beanContainer.beanInstance(EmbeddingModel.class);
        new EasyRagIngestor(embeddingModel, embeddingStore, runtimeConfig.getValue()).ingest();
    }

    public Supplier<InMemoryEmbeddingStore<TextSegment>> inMemoryEmbeddingStoreSupplier() {
        return new Supplier<>() {
            @Override
            public InMemoryEmbeddingStore<TextSegment> get() {
                if ((runtimeConfig.getValue().ingestionStrategy() == IngestionStrategy.ON)
                        && runtimeConfig.getValue().reuseEmbeddings().enabled()) {
                    // Want to reuse existing embeddings
                    Path embeddingsFile = Path.of(runtimeConfig.getValue().reuseEmbeddings().file()).toAbsolutePath();

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

    public Function<SyntheticCreationalContext<RetrievalAugmentor>, RetrievalAugmentor> easyRetrievalAugmentorFunction() {
        return new Function<>() {
            @Override
            public RetrievalAugmentor apply(SyntheticCreationalContext<RetrievalAugmentor> context) {
                EmbeddingModel model = context.getInjectedReference(EmbeddingModel.class, Default.Literal.INSTANCE);
                EmbeddingStore<TextSegment> store = context.getInjectedReference(EmbeddingStore.class,
                        Default.Literal.INSTANCE);
                return new EasyRetrievalAugmentor(runtimeConfig.getValue(), model, store);
            }
        };
    }
}
