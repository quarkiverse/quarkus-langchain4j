package io.quarkiverse.langchain4j.agentic.deployment.decision;

import static org.junit.jupiter.api.Assertions.fail;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.decision.Decide;
import io.quarkus.test.QuarkusExtensionTest;

public class PromptOnDecisionAgentShouldFailTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .assertException(throwable -> Assertions.assertThat(throwable)
                    .hasStackTraceContaining("must not use @UserMessage"));

    public interface SpamGate {
        @UserMessage("Is {{message}} spam?")
        @Decide("Is this message spam?")
        @Agent(description = "Detects spam", outputKey = "spam")
        boolean isSpam(@V("message") String message);
    }

    @Test
    public void test() {
        fail("should never be called");
    }
}
