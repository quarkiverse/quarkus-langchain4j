package io.quarkiverse.langchain4j.neo4j;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkiverse.langchain4j.EmbeddingStoreName;
import io.quarkus.test.QuarkusUnitTest;

public class Neo4jMultipleNamedStoresTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.neo4j.default-store-enabled", "false")
            .overrideConfigKey("quarkus.langchain4j.neo4j.products.database-name", "neo4j")
            .overrideRuntimeConfigKey("quarkus.langchain4j.neo4j.products.dimension", "1536")
            .overrideRuntimeConfigKey("quarkus.langchain4j.neo4j.products.label", "Product")
            .overrideRuntimeConfigKey("quarkus.langchain4j.neo4j.products.index-name", "product_vector")
            .overrideConfigKey("quarkus.langchain4j.neo4j.documents.database-name", "neo4j")
            .overrideRuntimeConfigKey("quarkus.langchain4j.neo4j.documents.dimension", "768")
            .overrideRuntimeConfigKey("quarkus.langchain4j.neo4j.documents.label", "Document")
            .overrideRuntimeConfigKey("quarkus.langchain4j.neo4j.documents.index-name", "document_vector");

    @Inject
    @EmbeddingStoreName("products")
    EmbeddingStore<TextSegment> productsEmbeddingStore;

    @Inject
    @EmbeddingStoreName("documents")
    EmbeddingStore<TextSegment> documentsEmbeddingStore;

    @Test
    void testBothNamed() {
        assertThat(productsEmbeddingStore).isNotNull();
        assertThat(documentsEmbeddingStore).isNotNull();
    }

    @Test
    void testNotSame() {
        assertThat(productsEmbeddingStore).isNotSameAs(documentsEmbeddingStore);
    }
}
