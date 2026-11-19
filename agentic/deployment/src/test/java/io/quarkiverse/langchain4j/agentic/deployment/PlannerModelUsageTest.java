package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.AgentListenerSupplier;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.declarative.SupervisorAgent;
import dev.langchain4j.agentic.observability.AgentInvocation;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.observability.MonitoredAgent;
import dev.langchain4j.agentic.observability.MonitoredExecution;
import dev.langchain4j.agentic.observability.PlannerModelResponse;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.output.OutputParsingException;
import io.quarkiverse.langchain4j.agentic.runtime.CdiBean;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * The model calls that a supervisor makes to plan its work are reported to its listeners and counted on its own
 * invocation by the monitor, and the {@link AgentListener} beans are notified of the agents orchestrating subagents
 * too, like a supervisor or a workflow, without any annotation on those agents.
 */
public class PlannerModelUsageTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            // the chat models are provided by the test
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "unused");

    private static final String PLANNING_MODEL_NAME = "planning-model";
    private static final String INVOKE_CLOCK = "{\"agentName\": \"clock\", \"arguments\": {\"question\": \"what time is it?\"}}";
    private static final String DONE = "{\"agentName\": \"done\", \"arguments\": {\"response\": \"It is noon\"}}";

    // ── Models ──

    /**
     * Provides the model the supervisors plan with, which replies what the test scripted, call after call.
     */
    @ApplicationScoped
    public static class PlanningModel {

        private final List<ChatResponse> replies = new CopyOnWriteArrayList<>();
        private final AtomicInteger calls = new AtomicInteger();

        public void script(ChatResponse... replies) {
            this.replies.clear();
            this.replies.addAll(List.of(replies));
            calls.set(0);
        }

        public ChatModel model() {
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest request) {
                    return replies.get(calls.getAndIncrement());
                }
            };
        }
    }

    private static ChatResponse reply(String text, TokenUsage tokenUsage) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(text))
                .modelName(PLANNING_MODEL_NAME)
                .tokenUsage(tokenUsage)
                .build();
    }

    // ── Agents ──

    public static class Clock {
        @Agent(name = "clock", description = "Tells what time it is", outputKey = "time")
        public static String tell(@V("question") String question) {
            return "noon";
        }
    }

    /** A supervisor with no listener of its own: only the listener beans observe it. */
    public interface BareSupervisor {
        @SupervisorAgent(name = "bareSupervisor", outputKey = "answer", subAgents = Clock.class, responseStrategy = SupervisorResponseStrategy.SUMMARY)
        String invoke(@V("request") String request);

        @ChatModelSupplier
        static ChatModel planner(@CdiBean PlanningModel planningModel) {
            return planningModel.model();
        }
    }

    /** A supervisor with a listener of its own and a monitor. Its subagent calls no model. */
    public interface ObservedSupervisor extends MonitoredAgent {
        @SupervisorAgent(name = "observedSupervisor", outputKey = "answer", subAgents = Clock.class, responseStrategy = SupervisorResponseStrategy.SUMMARY)
        String invoke(@V("request") String request);

        @ChatModelSupplier
        static ChatModel planner(@CdiBean PlanningModel planningModel) {
            return planningModel.model();
        }

        @AgentListenerSupplier
        static AgentListener listener() {
            return new AgentListener() {
                @Override
                public void onPlannerModelResponse(PlannerModelResponse plannerModelResponse) {
                    TokenUsage tokenUsage = plannerModelResponse.tokenUsage();
                    PLANNING_CALLS.add(plannerModelResponse.agentName() + " " + plannerModelResponse.modelName() + " "
                            + tokenUsage.inputTokenCount() + "/" + tokenUsage.outputTokenCount());
                }
            };
        }
    }

    /**
     * The planning calls notified to the listener of {@link ObservedSupervisor}, each as the name of the agent, the
     * name of the model and its input and output tokens.
     */
    private static final List<String> PLANNING_CALLS = new CopyOnWriteArrayList<>();

    public interface Assistant {
        @UserMessage("Answer {{question}}")
        @Agent(name = "assistant", description = "Answers a question", outputKey = "answer")
        String answer(@V("question") String question);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest request) {
                    return ChatResponse.builder().aiMessage(AiMessage.from("It is noon")).build();
                }
            };
        }
    }

    public interface Pipeline {
        @SequenceAgent(name = "pipeline", outputKey = "answer", subAgents = Assistant.class)
        String process(@V("question") String question);
    }

    // ── Listener beans ──

    /**
     * What the listener beans are notified of, each under its own name.
     */
    @ApplicationScoped
    public static class Notifications {

        private final List<String> notifications = new CopyOnWriteArrayList<>();

        public void clear() {
            notifications.clear();
        }

        public void add(String listener, String event, String agentName) {
            notifications.add(listener + " " + event + " " + agentName);
        }

        public List<String> of(String listener) {
            List<String> listenerNotifications = new ArrayList<>();
            for (String notification : notifications) {
                if (notification.startsWith(listener + " ")) {
                    listenerNotifications.add(notification.substring(listener.length() + 1));
                }
            }
            return listenerNotifications;
        }
    }

    @ApplicationScoped
    public static class ListenerBean implements AgentListener {

        @Inject
        Notifications notifications;

        @Override
        public void afterAgentInvocation(AgentResponse agentResponse) {
            notifications.add("bean", "after", agentResponse.agentName());
        }

        @Override
        public void onPlannerModelResponse(PlannerModelResponse plannerModelResponse) {
            notifications.add("bean", "planning", plannerModelResponse.agentName());
        }
    }

    /** Registered on a workflow and on its AI subagents, which also inherit it from the workflow. */
    @ApplicationScoped
    public static class InheritedListenerBean implements AgentListener {

        @Inject
        Notifications notifications;

        @Override
        public void afterAgentInvocation(AgentResponse agentResponse) {
            notifications.add("inherited", "after", agentResponse.agentName());
        }

        @Override
        public boolean inheritedBySubagents() {
            return true;
        }
    }

    /** Like {@link InheritedListenerBean}, in the other scope a listener bean can have, which has no client proxy. */
    @Singleton
    public static class SingletonInheritedListenerBean implements AgentListener {

        @Inject
        Notifications notifications;

        @Override
        public void afterAgentInvocation(AgentResponse agentResponse) {
            notifications.add("singletonInherited", "after", agentResponse.agentName());
        }

        @Override
        public boolean inheritedBySubagents() {
            return true;
        }
    }

    @Inject
    BareSupervisor bareSupervisor;

    @Inject
    ObservedSupervisor observedSupervisor;

    @Inject
    Pipeline pipeline;

    @Inject
    PlanningModel planningModel;

    @Inject
    Notifications notifications;

    // ── Listener beans ──

    @Test
    void listenerBeanIsNotifiedOfThePlanningCallsOfASupervisor() {
        planningModel.script(reply(INVOKE_CLOCK, new TokenUsage(100, 20)), reply(DONE, new TokenUsage(30, 5)));
        notifications.clear();

        assertThat(bareSupervisor.invoke("What time is it?")).isEqualTo("It is noon");

        // the call choosing the clock, the invocation of the clock, the call ending the invocation, then the
        // invocation of the supervisor itself
        assertThat(notifications.of("bean")).containsExactly("planning bareSupervisor", "after clock",
                "planning bareSupervisor", "after bareSupervisor");
    }

    @Test
    void listenerBeanIsNotifiedOfTheInvocationsOfAWorkflowAndOfItsAiSubagent() {
        notifications.clear();

        assertThat(pipeline.process("what time is it?")).isEqualTo("It is noon");

        assertThat(notifications.of("bean")).containsExactly("after assistant", "after pipeline");
    }

    @Test
    void listenerBeanInheritedBySubagentsIsNotifiedOnceOfEachInvocation() {
        notifications.clear();

        pipeline.process("what time is it?");

        // registered on the workflow and on its AI subagent, which also inherits it from the workflow
        assertThat(notifications.of("inherited")).containsExactly("after assistant", "after pipeline");
        assertThat(notifications.of("singletonInherited")).containsExactly("after assistant", "after pipeline");
    }

    @Test
    void listenerBeanIsNotifiedOnceOfANonAiSubagent() {
        planningModel.script(reply(INVOKE_CLOCK, new TokenUsage(100, 20)), reply(DONE, new TokenUsage(30, 5)));
        notifications.clear();

        bareSupervisor.invoke("What time is it?");

        // the listener beans are registered on the non-AI agent too: the inherited ones reach it twice, from the
        // supervisor and from that registration, and are notified once
        assertThat(notifications.of("bean")).containsOnlyOnce("after clock");
        assertThat(notifications.of("inherited")).containsExactly("after clock", "after bareSupervisor");
        assertThat(notifications.of("singletonInherited")).containsExactly("after clock", "after bareSupervisor");
    }

    // ── Supervisor ──

    @Test
    void supervisorReportsEachPlanningCallToItsListenerAndItsMonitorCountsThem() {
        planningModel.script(reply(INVOKE_CLOCK, new TokenUsage(100, 20)), reply(DONE, new TokenUsage(30, 5)));
        PLANNING_CALLS.clear();
        AgentMonitor monitor = observedSupervisor.agentMonitor();
        monitor.clear();

        assertThat(observedSupervisor.invoke("What time is it?")).isEqualTo("It is noon");

        // the call choosing the clock, then the one ending the invocation
        assertThat(PLANNING_CALLS).containsExactly("observedSupervisor planning-model 100/20",
                "observedSupervisor planning-model 30/5");

        // the monitor counts the planning calls on the invocation of the supervisor, and its subagent calls no model
        List<MonitoredExecution> executions = monitor.successfulExecutions();
        assertThat(executions).hasSize(1);
        AgentInvocation invocation = executions.get(0).topLevelInvocations();
        assertThat(invocation.tokenUsage()).contains(new TokenUsage(130, 25));
        assertThat(invocation.modelName()).contains(PLANNING_MODEL_NAME);
        assertThat(invocation.nestedInvocations()).hasSize(1);
        assertThat(invocation.nestedInvocations().get(0).totalTokenCount()).isZero();
    }

    @Test
    void supervisorWhosePlanningReplyCannotBeParsedStillReportsThatCall() {
        planningModel.script(reply("this is not an agent invocation", new TokenUsage(100, 20)));
        PLANNING_CALLS.clear();

        assertThatThrownBy(new ThrowingCallable() {
            @Override
            public void call() {
                observedSupervisor.invoke("What time is it?");
            }
        }).isInstanceOf(OutputParsingException.class);

        // the model answered and was billed, even if the supervisor could not read its reply
        assertThat(PLANNING_CALLS).containsExactly("observedSupervisor planning-model 100/20");
    }
}
