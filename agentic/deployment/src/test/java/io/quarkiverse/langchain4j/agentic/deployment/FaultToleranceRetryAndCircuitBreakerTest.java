package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Inject;

import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

/**
 * F-1: Documents and verifies that MicroProfile FaultTolerance annotations
 * {@code @Retry} and {@code @CircuitBreaker} work correctly on agent interfaces.
 */
public class FaultToleranceRetryAndCircuitBreakerTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class)
                            .addClasses(RetryAgent.class, CircuitBreakerAgent.class,
                                    FailNTimesChatModel.class, AlwaysFailingChatModel.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "default-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    /** Fails the first {@code failCount} calls, then returns {@code successResponse}. */
    public static class FailNTimesChatModel implements ChatModel {
        private final AtomicInteger failsRemaining;
        private final String successResponse;

        public FailNTimesChatModel(int failCount, String successResponse) {
            this.failsRemaining = new AtomicInteger(failCount);
            this.successResponse = successResponse;
        }

        @Override
        public ChatResponse doChat(ChatRequest request) {
            if (failsRemaining.getAndDecrement() > 0) {
                throw new RuntimeException("deliberate failure for retry test");
            }
            return ChatResponse.builder().aiMessage(new AiMessage(successResponse)).build();
        }
    }

    /** Always throws RuntimeException. */
    public static class AlwaysFailingChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest request) {
            throw new RuntimeException("always fails");
        }
    }

    public interface RetryAgent {

        @UserMessage("{{q}}")
        @Agent(value = "Agent that retries on failure", outputKey = "retryAnswer")
        @Retry(maxRetries = 2)
        String answer(String q);

        @ChatModelSupplier
        static ChatModel model() {
            return new FailNTimesChatModel(1, "retried-success");
        }
    }

    public interface CircuitBreakerAgent {

        @UserMessage("{{q}}")
        @Agent(value = "Agent with circuit breaker", outputKey = "cbAnswer")
        @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 10000, delayUnit = java.time.temporal.ChronoUnit.MILLIS)
        String answer(String q);

        @ChatModelSupplier
        static ChatModel model() {
            return new AlwaysFailingChatModel();
        }
    }

    @Inject
    RetryAgent retryAgent;

    @Inject
    CircuitBreakerAgent circuitBreakerAgent;

    @Test
    void testRetrySucceedsAfterOneFailure() {
        // FailNTimesChatModel fails once, then succeeds; @Retry(maxRetries=2) covers it
        String result = retryAgent.answer("question");
        assertThat(result).isEqualTo("retried-success");
    }

    @Test
    void testCircuitBreakerOpensAfterThreshold() {
        // Trip the circuit: with requestVolumeThreshold=4 and failureRatio=0.5,
        // need 4 requests with >= 50% failures to open. AlwaysFailingChatModel ensures this.
        for (int i = 0; i < 4; i++) {
            assertThrows(AgentInvocationException.class, () -> circuitBreakerAgent.answer("q"));
        }
        // Circuit is now open — next call throws CircuitBreakerOpenException directly
        // (FT interceptor fires before agent interceptor, so it is not wrapped)
        assertThrows(
                org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException.class,
                () -> circuitBreakerAgent.answer("q"));
    }
}
