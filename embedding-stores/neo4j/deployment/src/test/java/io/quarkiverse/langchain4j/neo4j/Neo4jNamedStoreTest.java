package io.quarkiverse.langchain4j.neo4j;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkiverse.langchain4j.EmbeddingStoreName;
import io.quarkus.test.QuarkusUnitTest;

public class Neo4jNamedStoreTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.neo4j.dimension", "384")
            .overrideConfigKey("quarkus.langchain4j.neo4j.products.database-name", "neo4j")
            .overrideRuntimeConfigKey("quarkus.langchain4j.neo4j.products.dimension", "1536")
            .overrideRuntimeConfigKey("quarkus.langchain4j.neo4j.products.label", "Product")
            .overrideRuntimeConfigKey("quarkus.langchain4j.neo4j.products.index-name", "product_vector");

    @Inject
    Neo4jEmbeddingStore defaultEmbeddingStore;

    @Inject
    @EmbeddingStoreName("products")
    EmbeddingStore<TextSegment> productsEmbeddingStore;

    @Test
    void testDefault() {
        assertThat(defaultEmbeddingStore).isNotNull();
    }

    @Test
    void testNamed() {
        assertThat(productsEmbeddingStore).isNotNull();
    }

    @Test
    void testNotSame() {
        assertThat((Object) defaultEmbeddingStore).isNotSameAs(productsEmbeddingStore);
    }
}
