package io.quarkiverse.langchain4j.jlama.deployment;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

public class JlamaBootstrapSmokeTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addAsResource(
                    "application.properties",
                    "application.properties"))
            .overrideConfigKey("quarkus.langchain4j.jlama.chat-model.enabled", "false")
            .overrideConfigKey("quarkus.langchain4j.jlama.embedding-model.enabled", "false");

    @Test
    void smokeBootstrap() {
        // Verifies the extension boots cleanly with models disabled (no IllegalStateException from
        // ApplicationModelSerializer due to the removed <devMode><jvmOptions> block).
    }
}