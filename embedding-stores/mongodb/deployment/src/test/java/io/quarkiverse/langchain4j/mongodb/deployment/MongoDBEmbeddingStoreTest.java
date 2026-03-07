package io.quarkiverse.langchain4j.mongodb.deployment;

import static org.awaitility.Awaitility.await;
import static org.wildfly.common.Assert.assertTrue;

import java.time.Duration;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.mongodb.client.MongoClient;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import io.quarkus.test.QuarkusUnitTest;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MongoDBEmbeddingStoreTest extends EmbeddingStoreIT {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset(
                            """
                                    quarkus.langchain4j.mongodb.database-name=test
                                    quarkus.langchain4j.mongodb.index-name=vector_index
                                    quarkus.mongodb.devservices.enabled=true
                                    quarkus.langchain4j.mongodb.dimensions=384
                                    quarkus.compose.devservices.files=compose-devservices.yml
                                    quarkus.mongodb.devservices.properties.uuidRepresentation = standard
                                    """),
                            "application.properties")

            );

    @Inject
    EmbeddingStore<TextSegment> embeddingStore;

    @Inject
    MongoClient mongoClient;

    private static EmbeddingModel embeddingModel;

    @BeforeAll
    void ensureEverythingIsReady() {
        embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
        embeddingStore.removeAll();

        await()
                .pollInSameThread()
                .pollInterval(Duration.ofSeconds(2))
                .ignoreExceptions()
                .untilAsserted(() -> {
                    try (var indexCursor = mongoClient.getDatabase("test").getCollection("embeddings")
                            .listSearchIndexes()
                            .cursor()) {

                        if (indexCursor.hasNext()) {
                            var index = indexCursor.next();
                            assertTrue("READY".equalsIgnoreCase(index.getString("status")));
                        }
                    }
                });
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
