package io.quarkiverse.langchain4j.test.decision;

import static org.junit.jupiter.api.Assertions.fail;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.service.decision.Decide;
import dev.langchain4j.service.decision.ThresholdContext;
import dev.langchain4j.service.decision.ThresholdProvider;
import io.quarkiverse.langchain4j.RegisterDecisionService;
import io.quarkus.test.QuarkusExtensionTest;

public class DecisionServiceAmbiguousThresholdProvidersShouldFailTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(FixedDecisionModel.class))
            .assertException(throwable -> Assertions.assertThat(throwable)
                    .hasStackTraceContaining("The Decision Services use a single ThresholdProvider bean, but several exist")
                    .hasStackTraceContaining("StrictThresholds")
                    .hasStackTraceContaining("LenientThresholds"));

    @RegisterDecisionService
    public interface SpamFilter {
        @Decide("Is this message spam?")
        boolean isSpam(String message);
    }

    @ApplicationScoped
    public static class StrictThresholds implements ThresholdProvider {
        @Override
        public Double threshold(ThresholdContext context) {
            return 0.95;
        }
    }

    @ApplicationScoped
    public static class LenientThresholds implements ThresholdProvider {
        @Override
        public Double threshold(ThresholdContext context) {
            return 0.2;
        }
    }

    public static class Models {
        @Produces
        @ApplicationScoped
        DecisionModel decisionModel() {
            return new FixedDecisionModel("fixed").yesNo("isSpam", 0.9);
        }
    }

    @Test
    public void test() {
        fail("should never be called");
    }
}
