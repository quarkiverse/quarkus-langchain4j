package io.quarkiverse.langchain4j.weaviate.deployment;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import dev.langchain4j.store.embedding.weaviate.WeaviateEmbeddingStore;
import io.quarkus.logging.Log;
import io.quarkus.test.QuarkusUnitTest;
import io.weaviate.client.WeaviateClient;
import io.weaviate.client.base.Result;
import io.weaviate.client.v1.data.model.WeaviateObject;

/**
 * Tests injecting a WeaviateEmbeddingStore using CDI, configured using properties.
 */
class WeaviateEmbeddingStoreCDITest extends EmbeddingStoreIT {
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(
                            new StringAsset(String.format("quarkus.langchain4j.weaviate.metadata.keys=%s",
                                    Constants.METADATA_KEYS.stream().collect(Collectors.joining(",")))),
                            "application.properties"));

    @Inject
    WeaviateClient client;

    @Inject
    WeaviateEmbeddingStore embeddingStore;

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
    protected WeaviateEmbeddingStore embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Override
    protected void ensureStoreIsEmpty() {
    }

    protected void clearStore() {
        Result<List<WeaviateObject>> objects = client.data().objectsGetter().run();
        objects.getResult().forEach(o -> {
            Result<Boolean> result = client.data().deleter().withClassName("Default").withID(o.getId()).run();
            if (!result.getResult()) {
                Log.error("Failed to clear store:");
                result.getError().getMessages().forEach(Log::error);
            }
        });
    }
}
