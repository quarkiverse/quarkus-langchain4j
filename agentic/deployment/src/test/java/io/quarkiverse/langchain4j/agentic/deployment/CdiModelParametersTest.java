package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.PlannerAgent;
import dev.langchain4j.agentic.declarative.PlannerSupplier;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.patterns.decisionrouter.DecisionRouterPlanner;
import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.agentic.runtime.CdiBean;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.DecisionReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * The model beans of the providers used only by parameters of static agent methods are created, even if nothing
 * injects them.
 */
public class CdiModelParametersTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "default-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.planner.api-key", "planner-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.writer.api-key", "writer-key");

    public static class BillingExpert {

        @Agent(name = "billing", description = "Answers questions about invoices", outputKey = "answer")
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

    /**
     * Invokes the sub-agent named by a chat model.
     */
    public static class ChatModelPlanner implements Planner {

        private final ChatModel chatModel;
        private List<AgentInstance> subagents;

        public ChatModelPlanner(ChatModel chatModel) {
            this.chatModel = chatModel;
        }

        @Override
        public void init(InitPlanningContext initPlanningContext) {
            this.subagents = initPlanningContext.subagents();
        }

        @Override
        public Action firstAction(PlanningContext planningContext) {
            String name = chatModel.chat("Which agent should answer: billing or support?");
            return call(subagents.stream().filter(agent -> agent.name().equals(name)).findFirst().orElseThrow());
        }

        @Override
        public Action nextAction(PlanningContext planningContext) {
            return done(planningContext.agenticScope().readState("answer"));
        }
    }

    public interface ChatRouter {

        @PlannerAgent(outputKey = "answer", subAgents = { BillingExpert.class, SupportExpert.class })
        String route(@V("request") String request);

        @PlannerSupplier
        static Planner planner(@ModelName("planner") ChatModel chatModel) {
            return new ChatModelPlanner(chatModel);
        }
    }

    public interface DecisionRouter {

        @PlannerAgent(outputKey = "answer", subAgents = { BillingExpert.class, SupportExpert.class })
        String route(@V("request") String request);

        @PlannerSupplier
        static Planner planner(@CdiBean DecisionModel decisionModel) {
            return new DecisionRouterPlanner(decisionModel);
        }
    }

    public interface Writer {

        @UserMessage("Write a story about {{topic}}")
        @Agent(description = "Writes a story", outputKey = "story")
        String write(@V("topic") String topic);

        // a supplier with parameters provides the model at each invocation, inside an agentic system
        @ChatModelSupplier
        static ChatModel chatModel(@ModelName("writer") ChatModel chatModel) {
            return chatModel;
        }
    }

    public interface StoryWorkflow {

        @SequenceAgent(outputKey = "story", subAgents = { Writer.class })
        String write(@V("topic") String topic);
    }

    @Inject
    ChatRouter chatRouter;

    @Inject
    DecisionRouter decisionRouter;

    @Inject
    StoryWorkflow storyWorkflow;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void plannerUsesNamedChatModelBean() {
        mock.chat().reply("support");

        assertThat(chatRouter.route("The app crashes")).isEqualTo("support answer");

        assertThat(mock.chat().singleRequest().header("Authorization")).isEqualTo("Bearer planner-key");
    }

    @Test
    void plannerUsesDecisionModelBean() {
        mock.decision().reply(DecisionReply.of(
                DecisionReply.choice("decision-agent", "billing", Map.of("billing", 0.9, "support", 0.1), null)));

        assertThat(decisionRouter.route("Why was I charged twice?")).isEqualTo("billing answer");

        CapturedRequest request = mock.decision().singleRequest();
        assertThat(request.path()).isEqualTo("/v1/decisions");
        assertThat(request.header("Authorization")).isEqualTo("Bearer default-key");
    }

    @Test
    void chatModelSupplierUsesNamedChatModelBean() {
        mock.chat().reply("Once upon a time");

        assertThat(storyWorkflow.write("dragons")).isEqualTo("Once upon a time");

        assertThat(mock.chat().singleRequest().header("Authorization")).isEqualTo("Bearer writer-key");
    }
}
