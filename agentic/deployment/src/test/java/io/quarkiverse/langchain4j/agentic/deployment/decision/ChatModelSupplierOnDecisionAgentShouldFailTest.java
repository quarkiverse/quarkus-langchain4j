package io.quarkiverse.langchain4j.agentic.deployment.decision;

import static org.junit.jupiter.api.Assertions.fail;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.V;
import dev.langchain4j.service.decision.Decide;
import io.quarkus.test.QuarkusExtensionTest;

public class ChatModelSupplierOnDecisionAgentShouldFailTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .assertException(throwable -> Assertions.assertThat(throwable)
                    .hasStackTraceContaining("must not declare a @ChatModelSupplier method"));

    public interface SpamGate {
        @Decide("Is this message spam?")
        @Agent(description = "Detects spam", outputKey = "spam")
        boolean isSpam(@V("message") String message);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return null;
        }
    }

    @Test
    public void test() {
        fail("should never be called");
    }
}
