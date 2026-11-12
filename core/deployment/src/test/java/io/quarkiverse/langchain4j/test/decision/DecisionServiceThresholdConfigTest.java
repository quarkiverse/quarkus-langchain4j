package io.quarkiverse.langchain4j.test.decision;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.service.decision.Decide;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.RegisterDecisionService;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Every model answers "yes" with a probability of 0.6.
 */
public class DecisionServiceThresholdConfigTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(FixedDecisionModel.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.decision-service.StrictService.threshold", "0.7")
            .overrideRuntimeConfigKey("quarkus.langchain4j.decision-service.TunedService.threshold", "0.7")
            .overrideRuntimeConfigKey("quarkus.langchain4j.decision-service.TunedService.threshold-by-model.model-a",
                    "0.5")
            .overrideRuntimeConfigKey("quarkus.langchain4j.decision-service.OtherModelService.threshold", "0.7")
            .overrideRuntimeConfigKey(
                    "quarkus.langchain4j.decision-service.OtherModelService.threshold-by-model.model-a", "0.5");

    @RegisterDecisionService
    public interface StrictService {
        @Decide("Is this message spam?")
        boolean isSpam(String message);
    }

    @RegisterDecisionService
    public interface TunedService {
        @Decide("Is this message spam?")
        boolean isSpam(String message);
    }

    @RegisterDecisionService(modelName = "b")
    public interface OtherModelService {
        @Decide("Is this message spam?")
        boolean isSpam(String message);
    }

    @RegisterDecisionService
    public interface PlainService {
        @Decide("Is this message spam?")
        boolean isSpam(String message);
    }

    public static class Models {
        @Produces
        @ApplicationScoped
        DecisionModel modelA() {
            return new FixedDecisionModel("model-a").yesNo("isSpam", 0.6);
        }

        @Produces
        @ApplicationScoped
        @ModelName("b")
        DecisionModel modelB() {
            return new FixedDecisionModel("model-b").yesNo("isSpam", 0.6);
        }
    }

    @Inject
    StrictService strictService;

    @Inject
    TunedService tunedService;

    @Inject
    OtherModelService otherModelService;

    @Inject
    PlainService plainService;

    @Test
    void serviceThreshold() {
        assertThat(strictService.isSpam("Hello")).isFalse();
    }

    @Test
    void modelThresholdOverridesTheServiceOne() {
        assertThat(tunedService.isSpam("Hello")).isTrue();
    }

    @Test
    void modelThresholdOnlyAppliesToItsModel() {
        assertThat(otherModelService.isSpam("Hello")).isFalse();
    }

    @Test
    void defaultThreshold() {
        assertThat(plainService.isSpam("Hello")).isTrue();
    }
}
