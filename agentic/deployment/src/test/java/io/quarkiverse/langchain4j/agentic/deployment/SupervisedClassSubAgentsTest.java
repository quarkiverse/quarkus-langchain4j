package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.HumanInTheLoop;
import dev.langchain4j.agentic.declarative.SupervisorAgent;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.runtime.CdiBean;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * The parameters of the sub-agents of a supervisor are supplied by the supervisor, so no agent has to provide them as
 * an output key. This holds for the sub-agents declared on a class too: a bean with a human-in-the-loop or a non-AI
 * agent method with a body, and a plain class with a static agent method. Each of them takes here a parameter that
 * nothing else in the application provides.
 */
public class SupervisedClassSubAgentsTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            // the chat models are provided by the test
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "unused");

    private static final String DONE = "{\"agentName\": \"done\", \"arguments\": {\"response\": \"Refunded\"}}";

    /** What the sub-agents received, each as its name and its argument. */
    private static final List<String> RECEIVED = new CopyOnWriteArrayList<>();

    /**
     * Provides the model the supervisors plan with, which replies what the test scripted, call after call.
     */
    @ApplicationScoped
    public static class PlanningModel {

        private final List<String> replies = new CopyOnWriteArrayList<>();
        private final AtomicInteger calls = new AtomicInteger();

        public void script(String... replies) {
            this.replies.clear();
            this.replies.addAll(List.of(replies));
            calls.set(0);
        }

        public ChatModel model() {
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest request) {
                    return ChatResponse.builder().aiMessage(AiMessage.from(replies.get(calls.getAndIncrement()))).build();
                }
            };
        }
    }

    private static String invocation(String agentName, String argumentName, String argument) {
        return "{\"agentName\": \"%s\", \"arguments\": {\"%s\": \"%s\"}}".formatted(agentName, argumentName, argument);
    }

    // ── Sub-agents ──

    @ApplicationScoped
    public static class Approver {

        @HumanInTheLoop(name = "approver", description = "Asks a manager to approve a refund", outputKey = "approved")
        public boolean approve(@V("refundReason") String refundReason) {
            RECEIVED.add("approver " + refundReason);
            return true;
        }
    }

    /** No scope annotation: an application scoped bean. */
    public static class Stamper {

        @Agent(name = "stamper", description = "Stamps a document", outputKey = "stamped")
        public String stamp(@V("document") String document) {
            RECEIVED.add("stamper " + document);
            return "[" + document + "]";
        }
    }

    public static class Clock {

        @Agent(name = "clock", description = "Tells what time it is in a city", outputKey = "time")
        public static String tell(@V("city") String city) {
            RECEIVED.add("clock " + city);
            return "noon";
        }
    }

    // ── Supervisors ──

    public interface InterfaceSupervisor {

        @SupervisorAgent(outputKey = "answer", subAgents = { Approver.class, Stamper.class,
                Clock.class }, responseStrategy = SupervisorResponseStrategy.SUMMARY)
        String invoke(@V("request") String request);

        @ChatModelSupplier
        static ChatModel planner(@CdiBean PlanningModel planningModel) {
            return planningModel.model();
        }
    }

    @ApplicationScoped
    public static class ClassSupervisor {

        @Inject
        PlanningModel planningModel;

        @SupervisorAgent(outputKey = "answer", subAgents = { Approver.class, Stamper.class,
                Clock.class }, responseStrategy = SupervisorResponseStrategy.SUMMARY)
        public native String invoke(@V("request") String request);

        @ChatModelSupplier
        ChatModel planner() {
            return planningModel.model();
        }
    }

    @Inject
    InterfaceSupervisor interfaceSupervisor;

    @Inject
    ClassSupervisor classSupervisor;

    @Inject
    PlanningModel planningModel;

    @BeforeEach
    void script() {
        RECEIVED.clear();
        planningModel.script(invocation("approver", "refundReason", "damaged item"),
                invocation("stamper", "document", "refund"), invocation("clock", "city", "Rome"), DONE);
    }

    @Test
    void interfaceSupervisorSuppliesTheParametersOfItsClassSubAgents() {
        assertThat(interfaceSupervisor.invoke("Refund the order")).isEqualTo("Refunded");
        assertThat(RECEIVED).containsExactly("approver damaged item", "stamper refund", "clock Rome");
    }

    @Test
    void classSupervisorSuppliesTheParametersOfItsClassSubAgents() {
        assertThat(classSupervisor.invoke("Refund the order")).isEqualTo("Refunded");
        assertThat(RECEIVED).containsExactly("approver damaged item", "stamper refund", "clock Rome");
    }
}
