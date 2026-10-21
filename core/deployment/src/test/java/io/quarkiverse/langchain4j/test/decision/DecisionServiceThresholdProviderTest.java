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
import dev.langchain4j.service.decision.ThresholdContext;
import dev.langchain4j.service.decision.ThresholdProvider;
import io.quarkiverse.langchain4j.RegisterDecisionService;
import io.quarkus.test.QuarkusUnitTest;

public class DecisionServiceThresholdProviderTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(FixedDecisionModel.class));

    @RegisterDecisionService
    public interface SpamFilter {
        @Decide("Is this message spam?")
        boolean isSpam(String message);

        @Decide("Is this message abusive?")
        boolean isAbusive(String message);
    }

    @ApplicationScoped
    public static class Thresholds implements ThresholdProvider {
        @Override
        public Double threshold(ThresholdContext context) {
            // not used by any other bean, so it is kept only because it is a ThresholdProvider
            return context.questionName().equals("isSpam") ? 0.95 : null;
        }
    }

    public static class Models {
        @Produces
        @ApplicationScoped
        DecisionModel decisionModel() {
            return new FixedDecisionModel("fixed").yesNo("isSpam", 0.9).yesNo("isAbusive", 0.9);
        }
    }

    @Inject
    SpamFilter spamFilter;

    @Test
    void thresholdProviderBean() {
        assertThat(spamFilter.isSpam("You won a cruise!")).isFalse();
        assertThat(spamFilter.isAbusive("You won a cruise!")).isTrue();
    }
}
