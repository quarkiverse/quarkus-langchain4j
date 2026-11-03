package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.eclipse.microprofile.faulttolerance.ExecutionContext;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.faulttolerance.api.BeforeRetry;
import io.smallrye.faulttolerance.api.BeforeRetryHandler;
import io.smallrye.faulttolerance.api.ExponentialBackoff;

/**
 * {@code @Retry} is an interceptor binding, so it was always copied onto the generated implementation of an AI
 * service. The other fault tolerance annotations - {@code @ExponentialBackoff}, {@code @FibonacciBackoff},
 * {@code @CustomBackoff}, {@code @RetryWhen}, {@code @BeforeRetry}, {@code @CircuitBreakerName} - are not
 * interceptor bindings, yet the SmallRye Fault Tolerance interceptor reads them off the intercepted method. When
 * they are not copied they are silently ignored on AI service methods, although the very same annotations work on
 * a regular bean.
 */
public class AiServiceFaultToleranceAnnotationsTest {

    /**
     * The delay before the first retry. The backoff multiplies it by {@link #BACKOFF_FACTOR} for the retries that
     * follow, which is what makes the effect of {@code @ExponentialBackoff} observable.
     */
    private static final long INITIAL_DELAY = 100;

    private static final int BACKOFF_FACTOR = 10;

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Assistant.class, FailingChatModel.class, FailingChatModelSupplier.class,
                            CountingBeforeRetryHandler.class));

    @RegisterAiService(chatLanguageModelSupplier = FailingChatModelSupplier.class)
    public interface Assistant {

        @Retry(maxRetries = 2, delay = INITIAL_DELAY)
        @ExponentialBackoff(factor = BACKOFF_FACTOR)
        @BeforeRetry(CountingBeforeRetryHandler.class)
        String chat(@UserMessage String message);
    }

    public static class FailingChatModelSupplier implements Supplier<ChatModel> {

        @Override
        public ChatModel get() {
            return FailingChatModel.INSTANCE;
        }
    }

    public static class FailingChatModel implements ChatModel {

        static final FailingChatModel INSTANCE = new FailingChatModel();

        final AtomicInteger calls = new AtomicInteger();
        final AtomicInteger failures = new AtomicInteger();
        final List<Long> invocationNanos = Collections.synchronizedList(new ArrayList<>());

        @Override
        public ChatResponse doChat(ChatRequest request) {
            invocationNanos.add(System.nanoTime());
            calls.incrementAndGet();
            if (failures.getAndDecrement() > 0) {
                throw new IllegalStateException("simulated model failure");
            }
            return ChatResponse.builder().aiMessage(AiMessage.from("ok")).build();
        }

        /**
         * @return the delay this model observed between two consecutive invocations, in milliseconds
         */
        List<Long> delaysBetweenInvocationsInMillis() {
            synchronized (invocationNanos) {
                List<Long> delays = new ArrayList<>();
                for (int i = 1; i < invocationNanos.size(); i++) {
                    delays.add((invocationNanos.get(i) - invocationNanos.get(i - 1)) / 1_000_000);
                }
                return delays;
            }
        }
    }

    public static class CountingBeforeRetryHandler implements BeforeRetryHandler {

        static final AtomicInteger INVOCATIONS = new AtomicInteger();

        @Override
        public void handle(ExecutionContext context) {
            INVOCATIONS.incrementAndGet();
        }
    }

    @Inject
    Assistant assistant;

    @BeforeEach
    void reset() {
        FailingChatModel.INSTANCE.calls.set(0);
        FailingChatModel.INSTANCE.failures.set(0);
        FailingChatModel.INSTANCE.invocationNanos.clear();
        CountingBeforeRetryHandler.INVOCATIONS.set(0);
    }

    @Test
    @ActivateRequestContext
    void beforeRetryHandlerIsInvokedOnEveryRetry() {
        FailingChatModel.INSTANCE.failures.set(2);

        assertThat(assistant.chat("hello")).isEqualTo("ok");

        // the first attempt plus the two retries allowed by maxRetries = 2
        assertThat(FailingChatModel.INSTANCE.calls).hasValue(3);
        assertThat(CountingBeforeRetryHandler.INVOCATIONS).hasValue(2);
    }

    @Test
    @ActivateRequestContext
    void exponentialBackoffIsHonouredBetweenRetries() {
        // warm up, so that the measurement below is not distorted by class loading and JIT
        assertThat(assistant.chat("warm up")).isEqualTo("ok");
        reset();

        FailingChatModel.INSTANCE.failures.set(2);

        assertThat(assistant.chat("hello")).isEqualTo("ok");

        List<Long> delays = FailingChatModel.INSTANCE.delaysBetweenInvocationsInMillis();
        assertThat(delays).hasSize(2);

        // the delay of @Retry is only the starting point - the backoff multiplies it for every retry, so the
        // second retry waits roughly a second. Without @ExponentialBackoff the delay would stay at 100ms.
        assertThat(delays.get(1))
                .as("@ExponentialBackoff must multiply the delay of the second retry")
                .isGreaterThanOrEqualTo(6 * INITIAL_DELAY);
    }
}
