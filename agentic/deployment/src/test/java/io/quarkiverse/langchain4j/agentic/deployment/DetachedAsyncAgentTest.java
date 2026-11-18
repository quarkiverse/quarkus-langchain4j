package io.quarkiverse.langchain4j.agentic.deployment;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.declarative.ErrorHandler;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.runtime.CdiBean;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * The invocation of an agentic system does not wait for an asynchronous agent whose result no agent reads: it returns
 * while that agent is still running, and the result or the failure of the agent reaches the {@link AgentListener}
 * beans and the error handler afterwards, on the thread of the agent.
 */
public class DetachedAsyncAgentTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            // the model is provided by the test
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "unused");

    public static class RolloutAnalyst {
        @Agent(description = "Decides what to do with a rollout", outputKey = "decision")
        public static String decide(@V("rollout") String rollout) {
            return "promote " + rollout;
        }
    }

    public interface Remediation {
        @UserMessage("Apply the fix required by this decision: {{decision}}")
        @Agent(name = "remediate", description = "Applies the fix required by a decision", outputKey = "fix", async = true)
        String remediate(@V("decision") String decision);
    }

    public interface RolloutAnalysis {
        // no agent reads the fix, so the sequence does not wait for it
        @SequenceAgent(outputKey = "decision", subAgents = { RolloutAnalyst.class, Remediation.class })
        String analyze(@V("rollout") String rollout);

        @ErrorHandler
        static ErrorRecoveryResult onError(ErrorContext errorContext, @CdiBean Incidents incidents) {
            incidents.report(errorContext.agentName());
            return ErrorRecoveryResult.throwException();
        }
    }

    /**
     * The model of the remediation, which takes long: a call is held until the test releases it, and then answers or
     * fails as the test asked.
     */
    @ApplicationScoped
    public static class RemediationModel implements ChatModel {

        private volatile CountDownLatch called;
        private volatile CountDownLatch released;
        private volatile boolean failing;

        public void hold(boolean failing) {
            this.called = new CountDownLatch(1);
            this.released = new CountDownLatch(1);
            this.failing = failing;
        }

        public boolean awaitCall() throws InterruptedException {
            return called.await(10, SECONDS);
        }

        public void release() {
            released.countDown();
        }

        @Override
        public ChatResponse doChat(ChatRequest request) {
            called.countDown();
            try {
                // not forever, so that an invocation waiting for the agent fails the test instead of blocking it
                released.await(10, SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while holding the remediation", e);
            }
            if (failing) {
                throw new IllegalStateException("sandbox unavailable");
            }
            return ChatResponse.builder().aiMessage(AiMessage.from("fixed")).build();
        }
    }

    @ApplicationScoped
    public static class RemediationListener implements AgentListener {

        private volatile CountDownLatch notified = new CountDownLatch(1);
        private volatile Object output;
        private volatile Throwable error;
        private volatile Thread thread;

        public void reset() {
            notified = new CountDownLatch(1);
            output = null;
            error = null;
            thread = null;
        }

        public boolean awaitNotification() throws InterruptedException {
            return notified.await(10, SECONDS);
        }

        public Object output() {
            return output;
        }

        public Throwable error() {
            return error;
        }

        public Thread thread() {
            return thread;
        }

        @Override
        public void afterAgentInvocation(AgentResponse agentResponse) {
            if (agentResponse.agentName().equals("remediate")) {
                output = agentResponse.output();
                thread = Thread.currentThread();
                notified.countDown();
            }
        }

        @Override
        public void onAgentInvocationError(AgentInvocationError agentInvocationError) {
            if (agentInvocationError.agentName().equals("remediate")) {
                error = agentInvocationError.error();
                thread = Thread.currentThread();
                notified.countDown();
            }
        }
    }

    /**
     * The bean the error handler of the agentic system reports the failures to.
     */
    @ApplicationScoped
    public static class Incidents {

        private final List<String> failedAgents = new CopyOnWriteArrayList<>();
        private volatile CountDownLatch reported = new CountDownLatch(1);
        private volatile Thread thread;

        public void reset() {
            failedAgents.clear();
            reported = new CountDownLatch(1);
            thread = null;
        }

        public void report(String agentName) {
            failedAgents.add(agentName);
            thread = Thread.currentThread();
            reported.countDown();
        }

        public boolean awaitReport() throws InterruptedException {
            return reported.await(10, SECONDS);
        }

        public List<String> failedAgents() {
            return failedAgents;
        }

        public Thread thread() {
            return thread;
        }
    }

    @Inject
    RolloutAnalysis rolloutAnalysis;

    @Inject
    RemediationModel model;

    @Inject
    RemediationListener listener;

    @Inject
    Incidents incidents;

    @Test
    void invocationReturnsBeforeAnAsyncAgentNobodyReadsCompletesAndItsResultReachesTheListenerBean()
            throws InterruptedException {
        model.hold(false);
        listener.reset();

        assertThat(rolloutAnalysis.analyze("canary")).isEqualTo("promote canary");

        // the invocation returned while the asynchronous agent is still calling its model, which the test holds
        assertThat(model.awaitCall()).as("the asynchronous agent called its model").isTrue();
        assertThat(listener.output()).isNull();

        model.release();
        assertThat(listener.awaitNotification()).as("the listener bean was notified of the asynchronous agent").isTrue();
        assertThat(listener.output()).isEqualTo("fixed");
        assertThat(listener.thread()).isNotSameAs(Thread.currentThread());
    }

    @Test
    void failureOfAnAsyncAgentNobodyReadsReachesTheListenerBeanAndTheErrorHandlerOnly() throws InterruptedException {
        model.hold(true);
        listener.reset();
        incidents.reset();

        // the fix is going to fail, but the caller already has the result of the invocation and gets no exception
        assertThat(rolloutAnalysis.analyze("canary")).isEqualTo("promote canary");
        assertThat(incidents.failedAgents()).isEmpty();

        model.release();
        assertThat(listener.awaitNotification()).as("the listener bean was notified of the asynchronous agent").isTrue();
        assertThat(listener.error()).hasStackTraceContaining("sandbox unavailable");
        assertThat(listener.output()).isNull();
        // the error handler got its CDI bean on the thread of the agent, after the invocation returned
        assertThat(incidents.awaitReport()).as("the error handler reported the failure").isTrue();
        assertThat(incidents.failedAgents()).containsExactly("remediate");
        assertThat(incidents.thread()).isNotSameAs(Thread.currentThread());
    }
}
