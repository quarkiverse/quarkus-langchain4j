package io.quarkiverse.langchain4j.openai.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.decision.DecisionModel;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Without an API key, and with the default base URL, the decision model bean must refuse to start, like the other
 * OpenAI models.
 */
public class DecisionModelMissingApiKeyTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .assertException(t -> assertThat(t).hasMessageContaining("quarkus.langchain4j.openai.api-key"));

    @Inject
    DecisionModel model;

    @Test
    void test() {
        fail("Should not be called");
    }
}
