package io.quarkiverse.langchain4j.hibernate.milvus.deployment;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithoutMetadataIT;
import io.quarkus.test.QuarkusUnitTest;

public class MilvusEmbeddingStoreTest extends EmbeddingStoreWithoutMetadataIT {

    public static final String COLLECTION_NAME = "test_embeddings";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset(
                            "quarkus.langchain4j.generic.table=" + COLLECTION_NAME + "\n" +
                                    "quarkus.langchain4j.generic.consistency-level=STRONG\n" +
                                    "quarkus.langchain4j.generic.dimension=384\n" +
                                    "quarkus.langchain4j.generic.schema-management.strategy=drop-and-create\n" +
                                    "quarkus.class-loading.parent-first-artifacts=ai.djl.huggingface:tokenizers\n" +
                                    "quarkus.log.category.\"io.quarkiverse.langchain4j.hibernate\".level=DEBUG\n\n"),
                            "application.properties"));

    private static final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @Inject
    protected EmbeddingStore<TextSegment> hibernateEmbeddingStore;

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Override
    protected void clearStore() {
        hibernateEmbeddingStore.removeAll();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return hibernateEmbeddingStore;
    }

}
