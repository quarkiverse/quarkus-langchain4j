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

public class LanceDbMissingApiKeyTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.lancedb.database", "test-db")
            .overrideRuntimeConfigKey("quarkus.langchain4j.lancedb.dimension", "384");

    @Inject
    LanceDbEmbeddingStore store;

    @Test
    void testMissingApiKey() {
        assertThatThrownBy(() -> store.toString())
                .hasCauseInstanceOf(ConfigValidationException.class);
    }
}
