package io.quarkiverse.langchain4j.pgvector.test;

import java.sql.SQLException;

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
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import io.quarkiverse.langchain4j.pgvector.PgVectorEmbeddingStore;
import io.quarkus.test.QuarkusUnitTest;

public class LangChain4jPgvectorTest extends EmbeddingStoreIT {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset("quarkus.langchain4j.pgvector.dimension=384\n"),
                            "application.properties"));

    @Inject
    PgVectorEmbeddingStore pgvectorEmbeddingStore;

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
        try {
            pgvectorEmbeddingStore.deleteAll();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return pgvectorEmbeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

}
