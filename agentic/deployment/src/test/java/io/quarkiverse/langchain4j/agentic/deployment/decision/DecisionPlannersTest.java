package io.quarkiverse.langchain4j.agentic.deployment.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

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
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.PlannerModelResponse;
import dev.langchain4j.agentic.patterns.cascade.ConfidenceCascadePlanner;
import dev.langchain4j.agentic.patterns.decisionrouter.DecisionRouterPlanner;
import dev.langchain4j.agentic.patterns.decisionscheduler.DecisionSchedulerPlanner;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.runtime.CdiBean;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * The planners of langchain4j-agentic-patterns based on a decision model get the DecisionModel bean through a
 * {@code @PlannerSupplier} method, and report its decisions to the listeners of the planner agent, here a listener
 * bean.
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

    public static class Drafter {
        @Agent(name = "drafter", description = "Drafts an answer to a request", outputKey = "draft")
        public static String draft(@V("request") String request) {
            return "draft";
        }
    }

    public static class Reviewer {
        @Agent(name = "reviewer", description = "Reviews a draft and gives the answer", outputKey = "answer")
        public static String review(@V("draft") String draft) {
            return "reviewed " + draft;
        }
    }

    public interface Scheduler {
        @PlannerAgent(outputKey = "answer", subAgents = { Drafter.class, Reviewer.class })
        String answer(@V("request") String request);

        @PlannerSupplier
        static Planner planner(@CdiBean DecisionModel decisionModel) {
            return DecisionSchedulerPlanner.builder()
                    .decisionModel(decisionModel)
                    .goal("The request has a reviewed answer")
                    .goalThreshold(0.6)
                    .activationThreshold(0.3)
                    .build();
        }
    }

    public static class Models {
        @Produces
        @ApplicationScoped
        DecisionModel decisionModel() {
            return new FixedDecisionModel("fixed")
                    .choice("decision-agent", "support", Map.of("billing", 0.2, "support", 0.8))
                    .yesNo("accepted", 0.3)
                    // the scheduler asks how useful each of its subagents that can run would be
                    .scale("drafter", 3, List.of(0.0, 0.0, 0.0, 1.0))
                    .scale("reviewer", 3, List.of(0.0, 0.0, 0.0, 1.0));
        }
    }

    /**
     * Records the decisions reported by the planners, each as the name of the model and its input and output tokens.
     */
    @ApplicationScoped
    public static class PlannerDecisions implements AgentListener {

        private final List<String> decisions = new CopyOnWriteArrayList<>();

        public void clear() {
            decisions.clear();
        }

        public List<String> decisions() {
            return decisions;
        }

        @Override
        public void onPlannerModelResponse(PlannerModelResponse plannerModelResponse) {
            TokenUsage tokenUsage = plannerModelResponse.tokenUsage();
            decisions.add(plannerModelResponse.modelName() + " " + tokenUsage.inputTokenCount() + "/"
                    + tokenUsage.outputTokenCount());
        }
    }

    @Inject
    Router router;

    @Inject
    Cascade cascade;

    @Inject
    Scheduler scheduler;

    @Inject
    PlannerDecisions plannerDecisions;

    @Test
    void plannerAgentIsNotAnAiService() {
        assertThatThrownBy(() -> Class.forName(Router.class.getName() + "$$QuarkusImpl", false,
                Thread.currentThread().getContextClassLoader())).isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void decisionRouterPlanner() {
        plannerDecisions.clear();

        assertThat(router.route("The app crashes when I log in")).isEqualTo("support answer");

        // the decision choosing the expert
        assertThat(plannerDecisions.decisions()).containsExactly("fixed 10/1");
    }

    @Test
    void confidenceCascadePlanner() {
        plannerDecisions.clear();

        // the answer of the cheap tier is not accepted, so the capable one answers
        assertThat(cascade.answer("Why was I charged twice?")).isEqualTo("capable answer");

        // the decision grading the cheap tier: the answer of the last tier is not graded
        assertThat(plannerDecisions.decisions()).containsExactly("fixed 10/1");
    }

    @Test
    void decisionSchedulerPlanner() {
        plannerDecisions.clear();

        assertThat(scheduler.answer("Why was I charged twice?")).isEqualTo("reviewed draft");

        // one decision before each subagent: after the reviewer no subagent can run, so the model is not asked again
        assertThat(plannerDecisions.decisions()).containsExactly("fixed 10/1", "fixed 10/1");
    }
}
