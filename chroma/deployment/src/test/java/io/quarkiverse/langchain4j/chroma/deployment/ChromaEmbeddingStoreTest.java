package io.quarkiverse.langchain4j.chroma.deployment;

import static dev.langchain4j.internal.Utils.randomUUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import io.quarkiverse.langchain4j.chroma.ChromaEmbeddingStore;
import io.quarkus.logging.Log;
import io.quarkus.test.QuarkusUnitTest;

class ChromaEmbeddingStoreTest extends EmbeddingStoreIT {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    @ConfigProperty(name = "quarkus.langchain4j.chroma.url")
    String chromaUrl;

    private ChromaEmbeddingStore embeddingStore;

    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @Override
    protected ChromaEmbeddingStore embeddingStore() {
        if (embeddingStore == null) {
            embeddingStore = ChromaEmbeddingStore.builder()
                    .baseUrl(chromaUrl)
                    .collectionName(randomUUID())
                    .build();
        }
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Override
    protected void clearStore() {
        try {
            embeddingStore().deleteAll(384);
        } catch (Exception e) {
            Log.warn(e);
        }
    }

}
