package io.quarkiverse.langchain4j.weaviate.deployment;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import dev.langchain4j.store.embedding.weaviate.WeaviateEmbeddingStore;
import io.quarkus.logging.Log;
import io.quarkus.test.QuarkusUnitTest;
import io.weaviate.client.Config;
import io.weaviate.client.WeaviateClient;
import io.weaviate.client.base.Result;
import io.weaviate.client.v1.data.model.WeaviateObject;

class WeaviateEmbeddingStoreTest extends EmbeddingStoreIT {
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(
                            new StringAsset(String.format("quarkus.langchain4j.weaviate.metadata.keys=%s",
                                    Constants.METADATA_KEYS.stream().collect(Collectors.joining(",")))),
                            "application.properties"));

    @ConfigProperty(name = "quarkus.langchain4j.weaviate.scheme")
    String weaviateScheme;

    @ConfigProperty(name = "quarkus.langchain4j.weaviate.host")
    String weaviateHost;

    @ConfigProperty(name = "quarkus.langchain4j.weaviate.port")
    Integer weaviatePort;

    @ConfigProperty(name = "quarkus.langchain4j.weaviate.metadata.keys")
    List<String> weaviateMetadataKeys;

    String weaviateClassName = "Default";

    private WeaviateEmbeddingStore embeddingStore;
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

    protected WeaviateEmbeddingStore embeddingStore() {
        if (embeddingStore == null) {
            embeddingStore = WeaviateEmbeddingStore.builder()
                    .scheme(weaviateScheme)
                    .host(weaviateHost)
                    .port(weaviatePort)
                    .objectClass(weaviateClassName)
                    .metadataKeys(weaviateMetadataKeys)
                    .build();
        }
        return embeddingStore;
    }

    @BeforeEach
    void beforeEach() {
        this.ensureStoreIsReady();
        this.clearStore();
        this.ensureStoreIsEmpty();
    }

    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    protected void ensureStoreIsReady() {

    }

    protected void ensureStoreIsEmpty() {
    }

    protected void clearStore() {
        WeaviateClient client = new WeaviateClient(new Config(weaviateScheme, weaviateHost + ":" + weaviatePort));
        Result<List<WeaviateObject>> objects = client.data().objectsGetter().run();
        objects.getResult().forEach(o -> {
            Result<Boolean> result = client.data().deleter().withClassName(weaviateClassName).withID(o.getId()).run();
            if (!result.getResult()) {
                Log.error("Failed to clear store:");
                result.getError().getMessages().forEach(Log::error);
            }
        });
    }
}
