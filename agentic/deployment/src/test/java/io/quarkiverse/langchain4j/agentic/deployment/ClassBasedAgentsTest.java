package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InterceptorBinding;
import jakarta.interceptor.InvocationContext;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.DecisionModelSupplier;
import dev.langchain4j.agentic.declarative.ExitCondition;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.declarative.Output;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.declarative.SupervisorAgent;
import dev.langchain4j.agentic.declarative.SupervisorRequest;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.decision.Decide;
import io.quarkiverse.langchain4j.agentic.deployment.decision.FixedDecisionModel;
import io.quarkiverse.langchain4j.agentic.runtime.AgentClassSupport;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ClientProxy;
import io.quarkus.arc.ManagedContext;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Verifies the class form of the agents: CDI beans whose agentic method is {@code native}, whose helpers are
 * instance methods, with their scopes and interceptors.
 */
public class ClassBasedAgentsTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(FixedDecisionModel.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    // ── interceptor ──

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.TYPE, ElementType.METHOD })
    @InterceptorBinding
    public @interface Logged {
    }

    @Interceptor
    @Logged
    @Priority(1)
    public static class LoggingInterceptor {

        static final List<String> INVOCATIONS = Collections.synchronizedList(new ArrayList<>());

        @AroundInvoke
        public Object log(InvocationContext ctx) throws Exception {
            INVOCATIONS.add(ctx.getMethod().getName());
            return ctx.proceed();
        }
    }

    // ── beans ──

    @ApplicationScoped
    public static class ReviewSettings {
        public double minimumScore() {
            return 0.8;
        }
    }

    @ApplicationScoped
    public static class ScoringRules {
        private final AtomicInteger calls = new AtomicInteger();

        public String nextScore() {
            return switch (calls.incrementAndGet()) {
                case 1 -> "0.3";
                case 2 -> "0.6";
                default -> "0.9";
            };
        }

        public void reset() {
            calls.set(0);
        }
    }

    @ApplicationScoped
    public static class Editor {
        public String polish(String story, double score) {
            return story + "!";
        }
    }

    // ── the style loop in the class form ──

    @ApplicationScoped
    public static class StyleScorer {

        @Inject
        ScoringRules rules;

        @UserMessage("Score the style of {{story}}")
        @Agent(description = "Scores the style of a story", outputKey = "score")
        @Logged
        public native double score(@V("story") String story);

        @ChatModelSupplier
        ChatModel model() {
            return new ChatModel() {
                @Override
                public dev.langchain4j.model.chat.response.ChatResponse doChat(
                        dev.langchain4j.model.chat.request.ChatRequest request) {
                    return dev.langchain4j.model.chat.response.ChatResponse.builder()
                            .aiMessage(new dev.langchain4j.data.message.AiMessage(rules.nextScore())).build();
                }
            };
        }
    }

    @ApplicationScoped
    public static class StyleEditor {

        @Inject
        Editor editor;

        @Agent(description = "Edits the style of a story", outputKey = "story")
        @Logged
        public String edit(@V("story") String story, @V("score") double score) {
            return editor.polish(story, score);
        }
    }

    @RequestScoped
    public static class StoryReview {

        static final AtomicReference<Object> LAST_MEMORY_ID = new AtomicReference<>();

        @Inject
        ReviewSettings settings;

        @LoopAgent(outputKey = "story", maxIterations = 5, subAgents = { StyleScorer.class, StyleEditor.class })
        @Logged
        public native String review(@V("story") String story);

        @ExitCondition
        boolean done(@V("score") double score) {
            return score >= settings.minimumScore();
        }

        @Output
        @Logged
        String save(AgenticScope scope, @V("story") String story) {
            LAST_MEMORY_ID.set(scope.memoryId());
            return story + " [saved]";
        }
    }

    @Inject
    StoryReview storyReview;

    @Inject
    ScoringRules scoringRules;

    @BeforeEach
    void reset() {
        LoggingInterceptor.INVOCATIONS.clear();
        scoringRules.reset();
    }

    @Test
    void loopWithInstanceHelpersAndInterceptors() {
        ManagedContext requestContext = Arc.container().requestContext();
        requestContext.activate();
        try {
            assertThat(storyReview.review("story")).isEqualTo("story!! [saved]");
        } finally {
            requestContext.terminate();
        }
        // the interceptors apply to the agent method of the root, to the ones of the sub-agents invoked by the
        // workflow, and to the helper methods
        assertThat(LoggingInterceptor.INVOCATIONS).containsExactly("review", "score", "edit", "score", "edit", "score",
                "save");
    }

    @Test
    void requestScopedAgentHasItsOwnAgenticScope() {
        ManagedContext requestContext = Arc.container().requestContext();
        AgenticScopeAccess root = (AgenticScopeAccess) AgentClassSupport.rootAgent(StoryReview.class);

        requestContext.activate();
        Object firstMemoryId;
        try {
            storyReview.review("first");
            firstMemoryId = StoryReview.LAST_MEMORY_ID.get();
            assertThat(root.getAgenticScope(firstMemoryId)).isNotNull();
            // the agentic scope is bound to the bean instance: a second call of the same instance reuses it
            scoringRules.reset();
            storyReview.review("again");
            assertThat(StoryReview.LAST_MEMORY_ID.get()).isEqualTo(firstMemoryId);
        } finally {
            requestContext.terminate();
        }
        // destroyed with the bean instance
        assertThat(root.getAgenticScope(firstMemoryId)).isNull();

        scoringRules.reset();
        requestContext.activate();
        try {
            storyReview.review("second");
            assertThat(StoryReview.LAST_MEMORY_ID.get()).isNotEqualTo(firstMemoryId);
        } finally {
            requestContext.terminate();
        }
    }

    // ── the expert router in the class form, with instance activation conditions ──

    @ApplicationScoped
    public static class MedicalExpert {
        @Agent(description = "A medical expert", outputKey = "answer")
        public String answer(@V("request") String request) {
            return "medical: " + request;
        }
    }

    @ApplicationScoped
    public static class LegalExpert {
        @Agent(description = "A legal expert", outputKey = "answer")
        public String answer(@V("request") String request) {
            return "legal: " + request;
        }
    }

    public static class ExpertRouter {

        @Inject
        ReviewSettings settings;

        @ConditionalAgent(outputKey = "answer", subAgents = { MedicalExpert.class, LegalExpert.class })
        public native String route(@V("category") String category, @V("request") String request);

        @ActivationCondition(MedicalExpert.class)
        boolean medical(@V("category") String category) {
            return settings != null && "medical".equals(category);
        }

        @ActivationCondition(LegalExpert.class)
        boolean legal(@V("category") String category) {
            return "legal".equals(category);
        }
    }

    @Inject
    ExpertRouter expertRouter;

    @Test
    void routerWithInstanceActivationConditions() {
        // a class-based agent without a scope annotation is an application scoped bean
        assertThat(ClientProxy.unwrap(expertRouter)).isSameAs(ClientProxy.unwrap(expertRouter));
        assertThat(expertRouter.route("medical", "flu")).isEqualTo("medical: flu");
        assertThat(expertRouter.route("legal", "contract")).isEqualTo("legal: contract");
        assertThat(expertRouter.route("other", "?")).isNull();
    }

    // ── a class-based decision agent ──

    @ApplicationScoped
    public static class SpamDetector {

        @Agent(description = "Detects spam")
        @Decide("Is this message spam?")
        public native boolean isSpam(@V("message") String message);

        @DecisionModelSupplier
        DecisionModel model() {
            return new FixedDecisionModel("fixed").yesNo("isSpam", 0.9);
        }
    }

    @Inject
    SpamDetector spamDetector;

    @Test
    void classBasedDecisionAgent() {
        assertThat(spamDetector.isSpam("Buy now!")).isTrue();
    }

    // ── a class-based sequence of class-based agents, invoked as a root and as a sub-agent ──

    @ApplicationScoped
    public static class Greeter {
        @Agent(description = "Greets", outputKey = "greeting")
        public String greet(@V("name") String name) {
            return "Hello " + name;
        }
    }

    @ApplicationScoped
    public static class GreetingSequence {
        @SequenceAgent(outputKey = "greeting", subAgents = Greeter.class)
        public native String greet(@V("name") String name);
    }

    @ApplicationScoped
    public static class ShoutingSequence {
        @SequenceAgent(outputKey = "shout", subAgents = GreetingSequence.class)
        public native String shout(@V("name") String name);

        @Output
        String output(@V("greeting") String greeting) {
            return greeting.toUpperCase() + "!";
        }
    }

    @Inject
    GreetingSequence greetingSequence;

    @Inject
    ShoutingSequence shoutingSequence;

    @Test
    void nestedClassBasedWorkflows() {
        assertThat(greetingSequence.greet("Mario")).isEqualTo("Hello Mario");
        assertThat(shoutingSequence.shout("Mario")).isEqualTo("HELLO MARIO!");
        // the sub-agent is the LangChain4j agent object of the class, shared with its bean
        AgentInstance root = (AgentInstance) AgentClassSupport.rootAgent(ShoutingSequence.class);
        assertThat(root.subagents()).hasSize(1);
        assertThat(root.subagents().get(0).type()).isEqualTo(GreetingSequence.class);
    }

    // ── a class-based supervisor ──

    @ApplicationScoped
    public static class StorySupervisor {

        @SupervisorAgent(outputKey = "story", subAgents = { Greeter.class })
        public native String write(@V("topic") String topic);

        @SupervisorRequest
        String request(@V("topic") String topic) {
            return "Write a story about " + topic;
        }

        @ChatModelSupplier
        ChatModel model() {
            return new FixedResponseChatModel("done");
        }
    }

    public static class FixedResponseChatModel implements ChatModel {

        private final String response;

        public FixedResponseChatModel(String response) {
            this.response = response;
        }

        @Override
        public dev.langchain4j.model.chat.response.ChatResponse doChat(
                dev.langchain4j.model.chat.request.ChatRequest request) {
            return dev.langchain4j.model.chat.response.ChatResponse.builder()
                    .aiMessage(new dev.langchain4j.data.message.AiMessage(response)).build();
        }
    }

    @Inject
    StorySupervisor storySupervisor;

    @Test
    void classBasedSupervisorIsCreated() {
        AgentInstance root = (AgentInstance) AgentClassSupport.rootAgent(StorySupervisor.class);
        assertThat(root.type()).isEqualTo(StorySupervisor.class);
        assertThat(root.subagents()).extracting(AgentInstance::type).containsExactly(Greeter.class);
    }
}
