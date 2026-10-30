package io.quarkiverse.langchain4j.typesafe.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.decision.DecisionModel;
import io.quarkus.test.QuarkusExtensionTest;

public class TypeSafeMissingApiKeyTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .assertException(t -> assertThat(t)
                    .hasMessageContaining("quarkus.langchain4j.typesafe.api-key"));

    @Inject
    DecisionModel decisionModel;

    @Test
    void test() {
        fail("Should not be called");
    }
}
