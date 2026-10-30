package io.quarkiverse.langchain4j.agentic.deployment.decision;

import static org.junit.jupiter.api.Assertions.fail;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.DecisionThresholdSupplier;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.service.V;
import dev.langchain4j.service.decision.Decide;
import dev.langchain4j.service.decision.ThresholdContext;
import io.quarkus.test.QuarkusExtensionTest;

public class DecisionAgentThresholdWithSupplierTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(FixedDecisionModel.class))
            .overrideConfigKey("quarkus.langchain4j.openai.decision-model.enabled", "false")
            .overrideRuntimeConfigKey("quarkus.langchain4j.agentic.decision-agent.spamGate.threshold", "0.7")
            .assertException(throwable -> Assertions.assertThat(throwable)
                    .hasStackTraceContaining("quarkus.langchain4j.agentic.decision-agent.\"spamGate\"")
                    .hasStackTraceContaining("@DecisionThresholdSupplier method, which takes precedence"));

    public interface SpamGate {
        @Decide("Is this message spam?")
        @Agent(name = "spamGate", description = "Detects spam", outputKey = "spam")
        boolean isSpam(@V("message") String message);

        @DecisionThresholdSupplier
        static double threshold(ThresholdContext context) {
            return 0.3;
        }
    }

    @Inject
    SpamGate spamGate;

    public static class Models {
        @Produces
        @ApplicationScoped
        DecisionModel decisionModel() {
            return new FixedDecisionModel("fixed");
        }
    }

    @Test
    public void test() {
        fail("should never be called");
    }
}
