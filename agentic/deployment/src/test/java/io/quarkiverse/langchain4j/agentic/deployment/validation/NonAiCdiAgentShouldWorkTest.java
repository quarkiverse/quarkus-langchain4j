package io.quarkiverse.langchain4j.agentic.deployment.validation;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.service.SystemMessage;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class NonAiCdiAgentShouldWorkTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @Test
    void test() {
        // build succeeded without validation errors — that's the assertion
    }

    public interface WorkflowWithCdiAgent {

        @SequenceAgent(outputKey = "result", subAgents = { SomeAiAgent.class, GreetingAgent.class })
        String execute();
    }

    public interface SomeAiAgent {

        @SystemMessage("You are a helpful assistant.")
        @Agent(description = "A helpful AI agent", outputKey = "input")
        String ask();
    }

    @ApplicationScoped
    public static class GreetingAgent {

        @Agent(description = "Produces a greeting", outputKey = "greeting")
        public String greet() {
            return "Hello from CDI!";
        }
    }
}
