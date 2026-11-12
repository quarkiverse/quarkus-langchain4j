package io.quarkiverse.langchain4j.test.decision;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.eclipse.microprofile.faulttolerance.Retry;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.decision.Decide;
import io.quarkiverse.langchain4j.RegisterDecisionService;
import io.quarkus.test.QuarkusExtensionTest;

public class DecisionServiceRetryTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(FixedDecisionModel.class));

    @RegisterDecisionService
    public interface SpamFilter {
        @Decide("Is this message spam?")
        @Retry(maxRetries = 2, delay = 0)
        boolean isSpam(String message);
    }

    public static class Models {
        @Produces
        @ApplicationScoped
        FixedDecisionModel decisionModel() {
            return new FixedDecisionModel("fixed").yesNo("isSpam", 0.9);
        }
    }

    @Inject
    SpamFilter spamFilter;

    @Inject
    FixedDecisionModel model;

    @Test
    void retried() {
        model.failing(1);

        assertThat(spamFilter.isSpam("You won a cruise!")).isTrue();
        assertThat(model.requests()).hasSize(2);
    }
}
