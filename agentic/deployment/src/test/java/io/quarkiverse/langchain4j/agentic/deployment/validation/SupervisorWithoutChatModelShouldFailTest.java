package io.quarkiverse.langchain4j.agentic.deployment.validation;

import static org.junit.jupiter.api.Assertions.fail;

import jakarta.inject.Inject;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.SupervisorAgent;
import dev.langchain4j.service.V;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Unlike the other agentic systems, a supervisor uses a chat model, for its planner.
 */
public class SupervisorWithoutChatModelShouldFailTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.openai.chat-model.enabled", "false")
            .assertException(throwable -> Assertions.assertThat(throwable)
                    .hasStackTraceContaining("A ChatModel or StreamingChatModel bean was requested"));

    public static class Billing {
        @Agent(description = "Answers questions about invoices", outputKey = "answer")
        public static String answer(@V("request") String request) {
            return "billing answer";
        }
    }

    public interface Supervisor {
        @SupervisorAgent(outputKey = "answer", subAgents = { Billing.class })
        String handle(@V("request") String request);
    }

    @Inject
    Supervisor supervisor;

    @Test
    public void test() {
        fail("should never be called");
    }
}
