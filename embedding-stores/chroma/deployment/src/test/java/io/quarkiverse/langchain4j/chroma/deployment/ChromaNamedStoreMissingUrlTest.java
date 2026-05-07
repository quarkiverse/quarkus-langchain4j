package io.quarkiverse.langchain4j.chroma.deployment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import io.quarkiverse.langchain4j.EmbeddingStoreName;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.config.ConfigValidationException;

public class ChromaNamedStoreMissingUrlTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset(
                            "quarkus.langchain4j.chroma.devservices.enabled=false\n" +
                                    "quarkus.langchain4j.chroma.default-store-enabled=false\n" +
                                    "quarkus.langchain4j.chroma.products.collection-name=product_embeddings\n"),
                            "application.properties"));

    @Inject
    @EmbeddingStoreName("products")
    ChromaEmbeddingStore productsEmbeddingStore;

    @Test
    void testMissingUrl() {
        assertThatThrownBy(() -> productsEmbeddingStore.toString())
                .hasCauseInstanceOf(ConfigValidationException.class);
    }
}
