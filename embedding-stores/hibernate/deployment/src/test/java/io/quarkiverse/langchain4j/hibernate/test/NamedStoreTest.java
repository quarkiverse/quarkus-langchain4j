package io.quarkiverse.langchain4j.hibernate.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.store.embedding.hibernate.EmbeddingEntity;
import dev.langchain4j.store.embedding.hibernate.HibernateEmbeddingStore;
import io.quarkiverse.langchain4j.EmbeddingStoreName;
import io.quarkus.test.QuarkusUnitTest;

public class NamedStoreTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset(
                            "quarkus.datasource.devservices.image-name=pgvector/pgvector:pg16\n" +
                                    "quarkus.langchain4j.generic.dimension=384\n" +
                                    "quarkus.langchain4j.generic.schema-management.strategy=drop-and-create\n" +
                                    "quarkus.langchain4j.generic.products.dimension=1536\n" +
                                    "quarkus.langchain4j.generic.products.table=product_embeddings\n" +
                                    "quarkus.langchain4j.generic.products.schema-management.strategy=drop-and-create\n" +
                                    "quarkus.class-loading.parent-first-artifacts=ai.djl.huggingface:tokenizers\n" +
                                    "quarkus.log.category.\"io.quarkiverse.langchain4j.hibernate\".level=DEBUG\n\n"),
                            "application.properties"));

    @Inject
    HibernateEmbeddingStore<EmbeddingEntity> defaultEmbeddingStore;

    @Inject
    @EmbeddingStoreName("products")
    HibernateEmbeddingStore<EmbeddingEntity> productsEmbeddingStore;

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
        assertThat(defaultEmbeddingStore).isNotSameAs(productsEmbeddingStore);
    }
}
