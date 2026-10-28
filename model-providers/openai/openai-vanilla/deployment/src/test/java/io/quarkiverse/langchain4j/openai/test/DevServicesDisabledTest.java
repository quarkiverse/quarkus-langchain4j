package io.quarkiverse.langchain4j.openai.test;

import static org.assertj.core.api.Assertions.assertThat;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;

/**
 * With the flag on but dev services disabled the mock cannot start; the build must fail instead of letting the
 * application silently call the real provider.
 */
public class DevServicesDisabledTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.devservices.enabled", "false")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "k")
            .assertException(t -> assertThat(t).hasStackTraceContaining("quarkus.langchain4j.mock-api.enabled")
                    .hasStackTraceContaining("quarkus.devservices.enabled"));

    @Test
    void buildFails() {
        // never runs: the build fails
    }
}
