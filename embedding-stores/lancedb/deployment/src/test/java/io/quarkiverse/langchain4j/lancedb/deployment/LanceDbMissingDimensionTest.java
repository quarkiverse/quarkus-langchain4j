package io.quarkiverse.langchain4j.lancedb.deployment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.lancedb.LanceDbEmbeddingStore;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.config.ConfigValidationException;

public class LanceDbMissingDimensionTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.lancedb.api-key", "test-api-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.lancedb.database", "test-db");

    @Inject
    LanceDbEmbeddingStore store;

    @Test
    void testMissingDimension() {
        assertThatThrownBy(() -> store.toString())
                .hasCauseInstanceOf(ConfigValidationException.class);
    }
}
