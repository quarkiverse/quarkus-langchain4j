package io.quarkiverse.langchain4j.agentic.deployment.decision;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.service.V;
import dev.langchain4j.service.decision.Decide;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * An application whose only agent is a decision agent starts without any chat model.
 */
public class DecisionOnlyApplicationTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(FixedDecisionModel.class))
            .overrideConfigKey("quarkus.langchain4j.openai.decision-model.enabled", "false")
            .overrideConfigKey("quarkus.langchain4j.openai.chat-model.enabled", "false");

    public interface SpamGate {
        @Decide("Is this message spam?")
        @Agent(description = "Detects spam", outputKey = "spam")
        boolean isSpam(@V("message") String message);
    }

    public static class Models {
        @Produces
        @ApplicationScoped
        DecisionModel decisionModel() {
            return new FixedDecisionModel("fixed").yesNo("isSpam", 0.2);
        }
    }

    @Inject
    SpamGate spamGate;

    @Inject
    Instance<ChatModel> chatModel;

    @Test
    void startsAndAnswersWithoutChatModel() {
        assertThat(chatModel.isResolvable()).isFalse();
        assertThat(spamGate.isSpam("Hello")).isFalse();
    }
}
