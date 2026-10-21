package io.quarkiverse.langchain4j.agentic.deployment.decision;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

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
import io.quarkiverse.langchain4j.ModelName;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Every agent gets a probability of "yes" of 0.6, or 0.4 for the one with a threshold supplier.
 */
public class DecisionAgentThresholdTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(FixedDecisionModel.class))
            .overrideConfigKey("quarkus.langchain4j.openai.decision-model.enabled", "false")
            .overrideRuntimeConfigKey("quarkus.langchain4j.agentic.decision-agent.strictGate.threshold", "0.7")
            .overrideRuntimeConfigKey("quarkus.langchain4j.agentic.decision-agent.tunedGate.threshold", "0.7")
            .overrideRuntimeConfigKey(
                    "quarkus.langchain4j.agentic.decision-agent.tunedGate.threshold-by-model.model-a", "0.5")
            .overrideRuntimeConfigKey("quarkus.langchain4j.agentic.decision-agent.otherModelGate.threshold", "0.7")
            .overrideRuntimeConfigKey(
                    "quarkus.langchain4j.agentic.decision-agent.otherModelGate.threshold-by-model.model-a", "0.5");

    public interface StrictGate {
        @Decide("Is this message spam?")
        @Agent(name = "strictGate", description = "A gate with a threshold", outputKey = "spam")
        boolean strict(@V("message") String message);
    }

    public interface TunedGate {
        @Decide("Is this message spam?")
        @Agent(name = "tunedGate", description = "A gate with a threshold for its model", outputKey = "spam")
        boolean tuned(@V("message") String message);
    }

    public interface OtherModelGate {
        @Decide("Is this message spam?")
        @Agent(name = "otherModelGate", description = "A gate with a threshold for another model", outputKey = "spam")
        @ModelName("b")
        boolean other(@V("message") String message);
    }

    public interface SuppliedThresholdGate {
        @Decide("Is this message spam?")
        @Agent(name = "suppliedThresholdGate", description = "A gate with a threshold supplier", outputKey = "spam")
        boolean supplied(@V("message") String message);

        @DecisionThresholdSupplier
        static double threshold(ThresholdContext context) {
            return 0.3;
        }
    }

    public interface PlainGate {
        @Decide("Is this message spam?")
        @Agent(name = "plainGate", description = "A gate with the default threshold", outputKey = "spam")
        boolean plain(@V("message") String message);
    }

    public static class Models {
        @Produces
        @ApplicationScoped
        DecisionModel modelA() {
            return new FixedDecisionModel("model-a")
                    .yesNo("strict", 0.6)
                    .yesNo("tuned", 0.6)
                    .yesNo("supplied", 0.4)
                    .yesNo("plain", 0.6);
        }

        @Produces
        @ApplicationScoped
        @ModelName("b")
        DecisionModel modelB() {
            return new FixedDecisionModel("model-b").yesNo("other", 0.6);
        }
    }

    @Inject
    StrictGate strictGate;

    @Inject
    TunedGate tunedGate;

    @Inject
    OtherModelGate otherModelGate;

    @Inject
    SuppliedThresholdGate suppliedThresholdGate;

    @Inject
    PlainGate plainGate;

    @Test
    void agentThreshold() {
        assertThat(strictGate.strict("Hello")).isFalse();
    }

    @Test
    void modelThresholdOverridesTheAgentOne() {
        assertThat(tunedGate.tuned("Hello")).isTrue();
    }

    @Test
    void modelThresholdOnlyAppliesToItsModel() {
        assertThat(otherModelGate.other("Hello")).isFalse();
    }

    @Test
    void thresholdSupplier() {
        assertThat(suppliedThresholdGate.supplied("Hello")).isTrue();
    }

    @Test
    void defaultThreshold() {
        assertThat(plainGate.plain("Hello")).isTrue();
    }
}
