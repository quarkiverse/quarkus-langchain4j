package io.quarkiverse.langchain4j.agentic.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;

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
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * The model beans of the providers used only by parameters of static agent methods are created, even if nothing
 * injects them.
 */
public class CdiModelParametersTest extends WiremockAware {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "default-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url", WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.planner.api-key", "planner-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.planner.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.writer.api-key", "writer-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.writer.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

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

    @BeforeEach
    void reset() {
        resetRequests();
    }

    @Test
    void plannerUsesNamedChatModelBean() {
        stubChatCompletion("support");

        assertThat(chatRouter.route("The app crashes")).isEqualTo("support answer");
        assertThat(singleLoggedRequest().getHeader("Authorization")).isEqualTo("Bearer planner-key");
    }

    @Test
    void plannerUsesDecisionModelBean() {
        wiremock().register(post(urlEqualTo("/v1/decisions"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "model": "gpt-6-luna",
                                  "answers": [{
                                    "type": "choice",
                                    "name": "decision-agent",
                                    "choice": "billing",
                                    "probabilities": [
                                      {"value": "billing", "probability": 0.9},
                                      {"value": "support", "probability": 0.1}
                                    ]
                                  }]
                                }
                                """)));

        assertThat(decisionRouter.route("Why was I charged twice?")).isEqualTo("billing answer");
        LoggedRequest request = singleLoggedRequest();
        assertThat(request.getUrl()).isEqualTo("/v1/decisions");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer default-key");
    }

    @Test
    void chatModelSupplierUsesNamedChatModelBean() {
        stubChatCompletion("Once upon a time");

        assertThat(storyWorkflow.write("dragons")).isEqualTo("Once upon a time");
        assertThat(singleLoggedRequest().getHeader("Authorization")).isEqualTo("Bearer writer-key");
    }

    /**
     * The default chat completion stub of the OpenAI testing module is missing when this module is tested on CI, so the
     * test registers its own, like the MCP agent tests.
     */
    private void stubChatCompletion(String content) {
        wiremock().register(post(urlEqualTo("/v1/chat/completions"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "chatcmpl-1",
                                  "object": "chat.completion",
                                  "created": 1698931202,
                                  "model": "gpt-6-luna",
                                  "choices": [{
                                    "index": 0,
                                    "message": {"role": "assistant", "content": "%s"},
                                    "finish_reason": "stop"
                                  }],
                                  "usage": {"prompt_tokens": 9, "completion_tokens": 12, "total_tokens": 21}
                                }
                                """.formatted(content))));
    }
}
