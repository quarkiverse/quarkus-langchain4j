package io.quarkiverse.langchain4j.neo4j;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import dev.langchain4j.store.embedding.neo4j.Neo4jEmbeddingStore;
import io.quarkus.test.QuarkusUnitTest;

public class Neo4jEmbeddingStoreTest extends EmbeddingStoreIT {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(
                            new StringAsset(
                                    "quarkus.langchain4j.neo4j.dimension=384\n" +
                                    // FIXME: this should be just neo4j:5, but version 5.21 has some
                                    // weird bug that causes the driver to crash, so hardcode the
                                    // test to use 5.20.0 for now
                                            "quarkus.neo4j.devservices.image-name=neo4j:5.20.0"),
                            "application.properties"));

    @Inject
    Neo4jEmbeddingStore embeddingStore;

    @Inject
    Driver driver;

    private static EmbeddingModel embeddingModel;

    // FIXME: Workaround for https://github.com/langchain4j/langchain4j/issues/776
    // Normally, we would just inject the model into the test
    @BeforeAll
    public static void initEmbeddingModel() {
        embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
    }

    @Override
    protected void clearStore() {
        try (Session session = driver.session()) {
            session.run("MATCH (n) CALL { WITH n DETACH DELETE n } IN TRANSACTIONS").consume();
        }
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
