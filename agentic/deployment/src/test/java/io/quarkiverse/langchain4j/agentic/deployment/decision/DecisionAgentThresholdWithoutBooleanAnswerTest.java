package io.quarkiverse.langchain4j.agentic.deployment.decision;

import static org.junit.jupiter.api.Assertions.fail;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.service.V;
import dev.langchain4j.service.decision.Decide;
import io.quarkus.test.QuarkusExtensionTest;

public class DecisionAgentThresholdWithoutBooleanAnswerTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(FixedDecisionModel.class))
            .overrideConfigKey("quarkus.langchain4j.openai.decision-model.enabled", "false")
            .overrideRuntimeConfigKey("quarkus.langchain4j.agentic.decision-agent.router.threshold", "0.7")
            .assertException(throwable -> Assertions.assertThat(throwable)
                    .hasStackTraceContaining("quarkus.langchain4j.agentic.decision-agent.\"router\"")
                    .hasStackTraceContaining("has no boolean answer"));

    public enum Team {
        BILLING,
        SUPPORT
    }

    public interface Router {
        @Decide("Which team should handle this request?")
        @Agent(name = "router", description = "Routes a request", outputKey = "team")
        Team route(@V("request") String request);
    }

    @Inject
    Router router;

    public static class Models {
        @Produces
        @ApplicationScoped
        DecisionModel decisionModel() {
            return new FixedDecisionModel("fixed");
        }
    }

    @Test
    public void test() {
        fail("should never be called");
    }
}
