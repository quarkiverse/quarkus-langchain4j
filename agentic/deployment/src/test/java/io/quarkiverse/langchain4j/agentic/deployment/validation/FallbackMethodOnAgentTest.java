package io.quarkiverse.langchain4j.agentic.deployment.validation;

import static org.junit.jupiter.api.Assertions.fail;

import org.assertj.core.api.Assertions;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.agentic.deployment.Agents;
import io.quarkus.test.QuarkusUnitTest;

public class FallbackMethodOnAgentTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(BrokenFallbackAgent.class, Agents.FixedResponseChatModel.class))
            .assertException(t -> Assertions.assertThat(t)
                    .isInstanceOf(IllegalConfigurationException.class)
                    .hasMessageContaining("fallbackMethod")
                    .hasMessageContaining("FallbackHandler"));

    public interface BrokenFallbackAgent {
        @UserMessage("Tell me a joke.")
        @Agent(description = "Agent with broken fallback")
        @Fallback(fallbackMethod = "doFallback")
        String answer();

        @ChatModelSupplier
        static ChatModel model() {
            return new Agents.FixedResponseChatModel("ok");
        }
    }

    @Test
    public void test() {
        fail("should never be called — build must fail with IllegalConfigurationException");
    }
}
