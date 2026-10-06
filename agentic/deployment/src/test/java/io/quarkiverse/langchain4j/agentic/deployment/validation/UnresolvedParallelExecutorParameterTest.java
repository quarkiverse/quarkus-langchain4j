package io.quarkiverse.langchain4j.agentic.deployment.validation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.Executor;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.agentic.declarative.ParallelExecutor;
import dev.langchain4j.agentic.planner.AgenticSystemConfigurationException;
import dev.langchain4j.service.UserMessage;
import io.quarkus.test.QuarkusUnitTest;

public class UnresolvedParallelExecutorParameterTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class)
                            .addClasses(FirstAgent.class, SecondAgent.class, EveningPlannerAgent.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "unused");

    @Inject
    EveningPlannerAgent agent;

    @Test
    public void unresolvedParameterFailsWhenAgentIsCreated() {
        assertThatThrownBy(() -> agent.plan())
                .hasRootCauseInstanceOf(AgenticSystemConfigurationException.class)
                .hasStackTraceContaining("No SupplierParameterResolver is registered for parameter")
                .hasStackTraceContaining("@ParallelExecutor");
    }

    public interface EveningPlannerAgent {

        @ParallelAgent(outputKey = "plans", subAgents = { FirstAgent.class, SecondAgent.class })
        List<String> plan();

        @ParallelExecutor
        static Executor executor(Object whatever) {
            return Runnable::run;
        }
    }

    public interface FirstAgent {

        @Agent(description = "First test agent", outputKey = "first")
        @UserMessage("first")
        String run();
    }

    public interface SecondAgent {

        @Agent(description = "Second test agent", outputKey = "second")
        @UserMessage("second")
        String run();
    }
}
