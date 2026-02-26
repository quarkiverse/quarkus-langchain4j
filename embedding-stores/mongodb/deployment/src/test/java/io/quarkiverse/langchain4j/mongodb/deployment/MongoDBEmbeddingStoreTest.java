package io.quarkiverse.langchain4j.mongodb.deployment;

import com.mongodb.client.MongoClient;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ArchivePaths;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import io.quarkus.test.QuarkusUnitTest;

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
    public static void initEmbeddingModel() {
        embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
    }


    @Override
    protected void ensureStoreIsReady() {
        while (true) {
            System.out.println("Waiting for index to be ready");
            var index = mongoClient.getDatabase("test").getCollection("embeddings")
                    .listSearchIndexes()
                    .first();
            if (index != null) {
                var ready = index.getString("status").equals("READY");
                if (ready) break;
            }
            try {
                Thread.currentThread().sleep(10000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
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
