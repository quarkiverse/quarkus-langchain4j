package io.quarkiverse.langchain4j.agentic.deployment.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.PlannerAgent;
import dev.langchain4j.agentic.declarative.PlannerSupplier;
import dev.langchain4j.agentic.patterns.cascade.ConfidenceCascadePlanner;
import dev.langchain4j.agentic.patterns.decisionrouter.DecisionRouterPlanner;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.runtime.CdiBean;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * The planners of langchain4j-agentic-patterns based on a decision model get the DecisionModel bean through a
 * {@code @PlannerSupplier} method.
 */
public class DecisionPlannersTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(FixedDecisionModel.class))
            // the decision model is produced by the test, and planner agents need no chat model
            .overrideConfigKey("quarkus.langchain4j.openai.decision-model.enabled", "false")
            .overrideConfigKey("quarkus.langchain4j.openai.chat-model.enabled", "false");

    public static class BillingExpert {
        @Agent(name = "billing", description = "Answers questions about invoices and payments", outputKey = "answer")
        public static String answer(@V("request") String request) {
            return "billing answer";
        }
    }

    public static class SupportExpert {
        @Agent(name = "support", description = "Answers questions about bugs", outputKey = "answer")
        public static String answer(@V("request") String request) {
            return "support answer";
        }
    }

    public interface Router {
        @PlannerAgent(outputKey = "answer", subAgents = { BillingExpert.class, SupportExpert.class })
        String route(@V("request") String request);

        @PlannerSupplier
        static Planner planner(@CdiBean DecisionModel decisionModel) {
            return new DecisionRouterPlanner(decisionModel);
        }
    }

    public static class CheapTier {
        @Agent(name = "cheap", description = "Answers on a small model", outputKey = "answer")
        public static String answer(@V("request") String request) {
            return "cheap answer";
        }
    }

    public static class CapableTier {
        @Agent(name = "capable", description = "Answers on a large model", outputKey = "answer")
        public static String answer(@V("request") String request) {
            return "capable answer";
        }
    }

    public interface Cascade {
        @PlannerAgent(outputKey = "answer", subAgents = { CheapTier.class, CapableTier.class })
        String answer(@V("request") String request);

        @PlannerSupplier
        static Planner planner(@CdiBean DecisionModel decisionModel) {
            return new ConfidenceCascadePlanner(decisionModel, 0.5);
        }
    }

    public static class Models {
        @Produces
        @ApplicationScoped
        DecisionModel decisionModel() {
            return new FixedDecisionModel("fixed")
                    .choice("decision-agent", "support", Map.of("billing", 0.2, "support", 0.8))
                    .yesNo("accepted", 0.3);
        }
    }

    @Inject
    Router router;

    @Inject
    Cascade cascade;

    @Test
    void plannerAgentIsNotAnAiService() {
        assertThatThrownBy(() -> Class.forName(Router.class.getName() + "$$QuarkusImpl", false,
                Thread.currentThread().getContextClassLoader())).isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void decisionRouterPlanner() {
        assertThat(router.route("The app crashes when I log in")).isEqualTo("support answer");
    }

    @Test
    void confidenceCascadePlanner() {
        // the answer of the cheap tier is not accepted, so the capable one answers
        assertThat(cascade.answer("Why was I charged twice?")).isEqualTo("capable answer");
    }
}
