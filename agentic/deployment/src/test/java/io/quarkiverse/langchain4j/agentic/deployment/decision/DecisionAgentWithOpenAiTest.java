package io.quarkiverse.langchain4j.agentic.deployment.decision;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;
import dev.langchain4j.service.decision.Decide;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.DecisionReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * A decision agent without supplier gets the decision model bean of the configured provider.
 */
public class DecisionAgentWithOpenAiTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.openai.decision-model.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "decision-key");

    public interface SpamGate {

        @Decide("Is this message spam?")
        @Agent(description = "Detects spam", outputKey = "spam")
        boolean isSpam(@V("message") String message);
    }

    @Inject
    SpamGate spamGate;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void decisionAgentUsesTheOpenAiDecisionModel() {
        mock.decision().reply(DecisionReply.of(DecisionReply.predicate("isSpam", 0.93)));

        assertThat(spamGate.isSpam("Congratulations! You won a free cruise!")).isTrue();

        assertThat(mock.requests()).hasSize(1);
        CapturedRequest request = mock.decision().singleRequest();
        assertThat(request.path()).isEqualTo("/v1/decisions");
        assertThat(request.header("Authorization")).isEqualTo("Bearer decision-key");
        assertThat(request.asDecision().questions()).singleElement()
                .satisfies(question -> assertThat(question.instructions()).isEqualTo("Is this message spam?"));
    }
}
