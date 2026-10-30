package io.quarkiverse.langchain4j.agentic.deployment.decision;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.eclipse.microprofile.faulttolerance.Retry;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.service.V;
import dev.langchain4j.service.decision.Decide;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * A decision agent whose model fails once is retried by fault tolerance, invoked directly and inside a workflow.
 */
public class DecisionAgentRetryTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(FixedDecisionModel.class))
            .overrideConfigKey("quarkus.langchain4j.openai.decision-model.enabled", "false");

    public interface SpamGate {
        @Decide("Is this message spam?")
        @Agent(description = "Detects spam", outputKey = "spam")
        @Retry(maxRetries = 2, delay = 0)
        boolean isSpam(@V("message") String message);
    }

    public static class Responder {
        @Agent(description = "Replies to a message", outputKey = "reply")
        public static String reply(@V("spam") boolean spam) {
            return spam ? "rejected" : "accepted";
        }
    }

    public interface SpamSequence {
        @SequenceAgent(outputKey = "reply", subAgents = { SpamGate.class, Responder.class })
        String handle(@V("message") String message);
    }

    public static class Models {
        @Produces
        @ApplicationScoped
        FixedDecisionModel decisionModel() {
            return new FixedDecisionModel("fixed").yesNo("isSpam", 0.9);
        }
    }

    @Inject
    SpamGate spamGate;

    @Inject
    SpamSequence spamSequence;

    @Inject
    FixedDecisionModel model;

    @Test
    void retriedWhenInvokedDirectly() {
        int requests = model.requests().size();
        model.failing(1);

        assertThat(spamGate.isSpam("You won a cruise!")).isTrue();
        assertThat(model.requests()).hasSize(requests + 2);
    }

    @Test
    void retriedInsideWorkflow() {
        int requests = model.requests().size();
        model.failing(1);

        assertThat(spamSequence.handle("You won a cruise!")).isEqualTo("rejected");
        assertThat(model.requests()).hasSize(requests + 2);
    }
}
