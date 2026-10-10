package io.quarkiverse.langchain4j.agentic.deployment.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.BeforeCall;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.invocation.InvocationParameters;
import io.quarkus.test.QuarkusUnitTest;

/**
 * A sub-agent can declare an {@link InvocationParameters} parameter that no agent outputs: langchain4j-agentic fills it
 * from the agentic scope's execution context, as it fills an {@link AgenticScope} parameter.
 */
public class InvocationParametersParameterTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    @Inject
    OrderWorkflow workflow;

    @Test
    void theSubAgentReceivesTheInvocationParametersFromTheScope() {
        assertThat(workflow.answer("A-42"))
                .isEqualTo("order A-42");
    }

    @ApplicationScoped
    public static class Caller {
        // makes orderId a caller-provided key, as in an application that injects its root
        @Inject
        OrderWorkflow workflow;
    }

    public interface OrderWorkflow {

        @SequenceAgent(outputKey = "answer", subAgents = OrderAgent.class)
        String answer(String orderId);

        @BeforeCall
        static void scopeToOrder(AgenticScope agenticScope) {
            agenticScope.writeExecutionContext(InvocationParameters.class,
                    InvocationParameters.from("orderId", agenticScope.readState("orderId")));
        }
    }

    public static class OrderAgent {

        @Agent(description = "Reads the order from the invocation parameters", outputKey = "answer")
        public static String answer(InvocationParameters invocationParameters) {
            return "order " + invocationParameters.get("orderId");
        }
    }
}
