package io.quarkiverse.langchain4j.oracle.test;

import jakarta.enterprise.inject.spi.DeploymentException;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

public class OracleNamedStoreInvalidDatasourceTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.oracle.default-store-enabled", "false")
            .overrideConfigKey("quarkus.langchain4j.oracle.products.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.oracle.products.datasource", "non-existent-ds")
            .overrideRuntimeConfigKey("quarkus.langchain4j.oracle.products.create-option", "CREATE_OR_REPLACE")
            .setExpectedException(DeploymentException.class);

    @Test
    void testInvalidDatasource() {
    }
}
