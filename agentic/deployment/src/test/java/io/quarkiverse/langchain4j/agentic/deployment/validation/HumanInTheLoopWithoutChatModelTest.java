package io.quarkiverse.langchain4j.agentic.deployment.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.HumanInTheLoop;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.V;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * A human-in-the-loop agent does not use a chat model, so an agentic system without AI agent needs none.
 */
public class HumanInTheLoopWithoutChatModelTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.openai.chat-model.enabled", "false");

    public interface RefundApprover {
        @HumanInTheLoop(description = "Asks a manager to approve the refund", outputKey = "approved")
        static boolean approve(@V("amount") int amount) {
            return amount < 100;
        }
    }

    public static class Refunder {
        @Agent(description = "Refunds an order", outputKey = "outcome")
        public static String refund(@V("approved") boolean approved) {
            return approved ? "refunded" : "rejected";
        }
    }

    public interface RefundWorkflow {
        @SequenceAgent(outputKey = "outcome", subAgents = { RefundApprover.class, Refunder.class })
        String refund(@V("amount") int amount);
    }

    @Inject
    RefundWorkflow refundWorkflow;

    @Inject
    Instance<ChatModel> chatModel;

    @Test
    void runsWithoutChatModel() {
        assertThat(chatModel.isResolvable()).isFalse();
        assertThat(refundWorkflow.refund(50)).isEqualTo("refunded");
        assertThat(refundWorkflow.refund(500)).isEqualTo("rejected");
    }
}
