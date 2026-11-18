package io.quarkiverse.langchain4j.agentic.deployment.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * The parameters of a root agent are passed by its caller, also when the build sees no injection point of the agent, as
 * in an application that only injects it in its tests or looks it up programmatically.
 */
public class RootAgentWithoutInjectionPointTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .withEmptyApplication();

    @Test
    void theRootAgentReceivesTheParametersOfItsCaller() {
        GreetingWorkflow workflow = Arc.container().select(GreetingWorkflow.class).get();

        assertThat(workflow.greet("Mario"))
                .isEqualTo("Hello Mario");
    }

    public interface GreetingWorkflow {

        @SequenceAgent(outputKey = "greeting", subAgents = GreetingAgent.class)
        String greet(String name);
    }

    public static class GreetingAgent {

        @Agent(description = "Greets the given name", outputKey = "greeting")
        public static String greet(String name) {
            return "Hello " + name;
        }
    }
}
