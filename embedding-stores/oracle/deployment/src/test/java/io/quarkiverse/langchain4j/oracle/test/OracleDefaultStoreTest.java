package io.quarkiverse.langchain4j.oracle.test;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

public class OracleDefaultStoreTest extends LangChain4jOracleBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.oracle.create-option", "CREATE_OR_REPLACE")
            .overrideConfigKey("quarkus.class-loading.parent-first-artifacts", "ai.djl.huggingface:tokenizers");
}
