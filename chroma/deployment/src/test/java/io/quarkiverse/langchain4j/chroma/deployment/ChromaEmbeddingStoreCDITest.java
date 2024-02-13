package io.quarkiverse.langchain4j.chroma.deployment;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import io.quarkiverse.langchain4j.chroma.ChromaEmbeddingStore;
import io.quarkus.logging.Log;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Tests injecting a ChromaEmbeddingStore using CDI, configured using properties.
 */
class ChromaEmbeddingStoreCDITest extends EmbeddingStoreIT {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    @Inject
    ChromaEmbeddingStore embeddingStore;

    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @Override
    protected ChromaEmbeddingStore embeddingStore() {
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
