package io.quarkiverse.langchain4j.pinecone.test;

import jakarta.enterprise.inject.Default;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.pinecone.PineconeEmbeddingStore;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;

public class PineconeDefaultStoreDisabledTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.pinecone.default-store-enabled", "false")
            .overrideConfigKey("quarkus.langchain4j.pinecone.products.index-name", "products-index")
            .overrideRuntimeConfigKey("quarkus.langchain4j.pinecone.products.api-key", "test-api-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.pinecone.products.environment", "gcp-starter")
            .overrideRuntimeConfigKey("quarkus.langchain4j.pinecone.products.project-id", "proj123")
            .overrideRuntimeConfigKey("quarkus.langchain4j.pinecone.products.dimension", "1536");

    @Test
    void testDefaultPineconeStoreNotAvailable() {
        var handle = Arc.container().instance(PineconeEmbeddingStore.class, Default.Literal.INSTANCE);
        Assertions.assertThat(handle.get()).isNull();
    }
}
