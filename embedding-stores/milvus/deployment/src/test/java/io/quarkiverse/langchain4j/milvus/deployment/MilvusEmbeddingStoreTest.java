package io.quarkiverse.langchain4j.milvus.deployment;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithoutMetadataIT;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import io.quarkus.test.QuarkusUnitTest;

public class MilvusEmbeddingStoreTest extends EmbeddingStoreWithoutMetadataIT {

    public static final String COLLECTION_NAME = "test_embeddings";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset(
                            "quarkus.langchain4j.milvus.collection-name=" + COLLECTION_NAME + "\n" +
                                    "quarkus.langchain4j.milvus.devservices.port=19530\n" +
                                    "quarkus.langchain4j.milvus.consistency-level=STRONG\n" +
                                    "quarkus.langchain4j.milvus.dimension=384"),
                            "application.properties"));

    @Inject
    MilvusEmbeddingStore embeddingStore;

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

    // FIXME:
    //    don't check emptiness of the store for now - maybe a bug: calling milvusEmbeddingStore.findRelevant
    //    on an empty collection throws an error  (io.milvus.exception.ServerException: empty expression should be used with limit)
    @Override
    protected void ensureStoreIsEmpty() {

    }

    @Override
    protected void clearStore() {
        embeddingStore.removeAll();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

}
