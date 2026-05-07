package io.quarkiverse.langchain4j.pgvector.test;

import jakarta.enterprise.inject.spi.DeploymentException;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

public class PgVectorNamedStoreInvalidDatasourceTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.pgvector.default-store-enabled", "false")
            .overrideConfigKey("quarkus.langchain4j.pgvector.products.datasource", "non-existent-ds")
            .overrideRuntimeConfigKey("quarkus.langchain4j.pgvector.products.dimension", "1536")
            .setExpectedException(DeploymentException.class);

    @Test
    void testInvalidDatasource() {
    }
}
