package io.quarkiverse.langchain4j.agentic.deployment.decision;

import static org.junit.jupiter.api.Assertions.fail;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.DecisionThresholdSupplier;
import dev.langchain4j.service.V;
import dev.langchain4j.service.decision.Decide;
import dev.langchain4j.service.decision.ThresholdContext;
import io.quarkus.test.QuarkusExtensionTest;

public class NonStaticDecisionThresholdSupplierShouldFailTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .assertException(throwable -> Assertions.assertThat(throwable)
                    .hasStackTraceContaining("DecisionThresholdSupplier' must be static"));

    public interface SpamGate {
        @Decide("Is this message spam?")
        @Agent(description = "Detects spam", outputKey = "spam")
        boolean isSpam(@V("message") String message);

        @DecisionThresholdSupplier
        default double threshold(ThresholdContext context) {
            return 0.5;
        }
    }

    @Test
    public void test() {
        fail("should never be called");
    }
}
