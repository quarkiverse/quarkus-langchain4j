package io.quarkiverse.langchain4j.qdrant;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithoutMetadataIT;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.quarkus.test.QuarkusUnitTest;

public class QdrantEmbeddingStoreTest extends EmbeddingStoreWithoutMetadataIT {

    public static final String COLLECTION_NAME = "qdrant_test_embeddings";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(QdrantEmbeddingStoreTest::archive);

    @Inject
    QdrantEmbeddingStore embeddingStore;

    private static EmbeddingModel embeddingModel;

    /**
     * FIXME: This is a workaround to avoid loading the embedding model in this test class' static initializer,
     * because otherwise we hit
     * java.lang.UnsatisfiedLinkError: Native Library (/path/to/the/library) already loaded in another classloader
     * because the test class is loaded by JUnit and by Quarkus in different class loaders.
     */
    @BeforeAll
    public static void initEmbeddingModel() {
        embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
    }

    @Override
    protected void clearStore() {
        embeddingStore.clearStore();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    private static JavaArchive archive() {
        Asset properties = new StringAsset(String.join("\n",
                "quarkus.langchain4j.qdrant.devservices.service-name=" + COLLECTION_NAME,
                "quarkus.langchain4j.qdrant.devservices.port=6334",
                "quarkus.langchain4j.qdrant.devservices.collection.vector-params.distance=Cosine",
                "quarkus.langchain4j.qdrant.devservices.collection.vector-params.size=384"));

        return ShrinkWrap.create(JavaArchive.class).addAsResource(properties, "application.properties");
    }

}
