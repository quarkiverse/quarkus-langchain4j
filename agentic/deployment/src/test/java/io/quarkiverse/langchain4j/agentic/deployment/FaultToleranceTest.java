package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.inject.Inject;

import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class FaultToleranceTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class)
                            .addClasses(FirstAgent.class, SecondAgentWithDelay.class, RouterAgent.class,
                                    Agents.FixedResponseChatModel.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "default-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    public interface FirstAgent {

        @UserMessage("Answer the following question: {{question}}")
        @Agent(value = "An agent always returning 'first'", outputKey = "answer")
        @Timeout(100)
        String answer(String question);

        @ChatModelSupplier
        static ChatModel model() {
            return new Agents.FixedResponseChatModel("first");
        }
    }

    public interface SecondAgentWithDelay {

        @UserMessage("Answer the following question: {{question}}")
        @Agent(value = "An agent always returning 'second'", outputKey = "answer")
        @Timeout(5)
        String answer(String question);

        @ChatModelSupplier
        static ChatModel model() {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return new Agents.FixedResponseChatModel("second");
        }
    }

    public interface RouterAgent {

        @ConditionalAgent(outputKey = "answer", subAgents = { FirstAgent.class, SecondAgentWithDelay.class })
        String ask(String question);

        @ActivationCondition(FirstAgent.class)
        static boolean activateMedical(String question) {
            return question.contains("1");
        }

        @ActivationCondition(SecondAgentWithDelay.class)
        static boolean activateTechnical(String question) {
            return question.contains("2");
        }
    }

    @Inject
    RouterAgent routerAgent;

    @Test
    void testWithoutTimeoutExpiration() {
        String result = routerAgent.ask("1");
        assertThat(result).isEqualTo("first");
    }

    @Test
    void testWithTimeoutExpiration() {
        assertThat(assertThrows(AgentInvocationException.class, () -> routerAgent.ask("2")))
                .hasRootCauseInstanceOf(TimeoutException.class);
    }
}
