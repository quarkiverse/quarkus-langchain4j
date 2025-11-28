package io.quarkiverse.langchain4j.test.guardrails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.GuardrailException;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailAccumulator;
import io.quarkiverse.langchain4j.guardrails.OutputTokenAccumulator;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;

public class OutputGuardrailMetricsTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyAiService.class,
                            SuccessOutputGuardrail.class,
                            FailureOutputGuardrail.class,
                            RepromptOutputGuardrail.class,
                            FirstOutputGuardrail.class,
                            SecondOutputGuardrail.class,
                            SecondOutputGuardrailWithFailure.class,
                            StreamingSuccessOutputGuardrail.class,
                            MyChatModel.class,
                            MyChatModelSupplier.class,
                            MyStreamingChatModel.class,
                            MyStreamingChatModelSupplier.class,
                            PassThroughAccumulator.class,
                            MeterRegistryProducer.class));

    @Inject
    MyAiService aiService;

    @Inject
    MeterRegistry registry;

    @Inject
    RepromptOutputGuardrail repromptGuardrail;

    @BeforeEach
    void clearRegistry() {
        registry.clear();
        repromptGuardrail.reset();
    }

    @Test
    @ActivateRequestContext
    void shouldRecordOutputGuardrailMetricsOnSuccess() {
        String result = aiService.chatWithSuccessGuardrail("hello");
        assertThat(result).isEqualTo("response");

        await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    Counter counter = registry.find("guardrail.invoked")
                            .tag("aiservice", MyAiService.class.getName())
                            .tag("operation", "chatWithSuccessGuardrail")
                            .tag("guardrail", SuccessOutputGuardrail.class.getName())
                            .tag("guardrail.type", "output")
                            .tag("outcome", "success")
                            .counter();

                    assertThat(counter)
                            .isNotNull()
                            .extracting(Counter::count)
                            .isEqualTo(1.0);
                });

        await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    Timer timer = registry.find("guardrail.timed")
                            .tag("aiservice", MyAiService.class.getName())
                            .tag("operation", "chatWithSuccessGuardrail")
                            .tag("guardrail", SuccessOutputGuardrail.class.getName())
                            .tag("guardrail.type", "output")
                            .tag("outcome", "success")
                            .timer();

                    assertThat(timer)
                            .isNotNull()
                            .extracting(Timer::count)
                            .isEqualTo(1L);

                    assertThat(timer.totalTime(TimeUnit.NANOSECONDS))
                            .isGreaterThan(0);
                });
    }

    @Test
    @ActivateRequestContext
    void shouldRecordOutputGuardrailMetricsOnFailure() {
        assertThatThrownBy(() -> aiService.chatWithFailureGuardrail("hello"))
                .isInstanceOf(GuardrailException.class)
                .hasMessageContaining("Output response is invalid");

        await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    Counter counter = registry.find("guardrail.invoked")
                            .tag("aiservice", MyAiService.class.getName())
                            .tag("operation", "chatWithFailureGuardrail")
                            .tag("guardrail", FailureOutputGuardrail.class.getName())
                            .tag("guardrail.type", "output")
                            .tag("outcome", "failure")
                            .counter();

                    assertThat(counter)
                            .isNotNull()
                            .extracting(Counter::count)
                            .isEqualTo(1.0);
                });

        await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    Timer timer = registry.find("guardrail.timed")
                            .tag("aiservice", MyAiService.class.getName())
                            .tag("operation", "chatWithFailureGuardrail")
                            .tag("guardrail", FailureOutputGuardrail.class.getName())
                            .tag("guardrail.type", "output")
                            .tag("outcome", "failure")
                            .timer();

                    assertThat(timer)
                            .isNotNull()
                            .extracting(Timer::count)
                            .isEqualTo(1L);
                });
    }

    @Test
    @ActivateRequestContext
    void shouldRecordOutputGuardrailMetricsOnReprompt() {
        String result = aiService.chatWithRepromptGuardrail("hello");
        assertThat(result).isEqualTo("response");

        assertThat(repromptGuardrail.getInvocationCount()).isEqualTo(2);

        await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    Counter repromptCounter = registry.find("guardrail.invoked")
                            .tag("aiservice", MyAiService.class.getName())
                            .tag("operation", "chatWithRepromptGuardrail")
                            .tag("guardrail", RepromptOutputGuardrail.class.getName())
                            .tag("guardrail.type", "output")
                            .tag("outcome", "reprompt")
                            .counter();

                    assertThat(repromptCounter)
                            .isNotNull()
                            .extracting(Counter::count)
                            .isEqualTo(1.0);
                });

        await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    Counter successCounter = registry.find("guardrail.invoked")
                            .tag("aiservice", MyAiService.class.getName())
                            .tag("operation", "chatWithRepromptGuardrail")
                            .tag("guardrail", RepromptOutputGuardrail.class.getName())
                            .tag("guardrail.type", "output")
                            .tag("outcome", "success")
                            .counter();

                    assertThat(successCounter)
                            .isNotNull()
                            .extracting(Counter::count)
                            .isEqualTo(1.0);
                });

        await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    Timer repromptTimer = registry.find("guardrail.timed")
                            .tag("aiservice", MyAiService.class.getName())
                            .tag("operation", "chatWithRepromptGuardrail")
                            .tag("guardrail", RepromptOutputGuardrail.class.getName())
                            .tag("guardrail.type", "output")
                            .tag("outcome", "reprompt")
                            .timer();

                    assertThat(repromptTimer)
                            .isNotNull()
                            .extracting(Timer::count)
                            .isEqualTo(1L);

                    Timer successTimer = registry.find("guardrail.timed")
                            .tag("aiservice", MyAiService.class.getName())
                            .tag("operation", "chatWithRepromptGuardrail")
                            .tag("guardrail", RepromptOutputGuardrail.class.getName())
                            .tag("guardrail.type", "output")
                            .tag("outcome", "success")
                            .timer();

                    assertThat(successTimer)
                            .isNotNull()
                            .extracting(Timer::count)
                            .isEqualTo(1L);
                });
    }

    @Test
    @ActivateRequestContext
    void shouldRecordMultipleInvocations() {
        aiService.chatWithSuccessGuardrail("hello");
        aiService.chatWithSuccessGuardrail("world");
        aiService.chatWithSuccessGuardrail("test");

        await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    Counter counter = registry.find("guardrail.invoked")
                            .tag("aiservice", MyAiService.class.getName())
                            .tag("operation", "chatWithSuccessGuardrail")
                            .tag("guardrail", SuccessOutputGuardrail.class.getName())
                            .tag("guardrail.type", "output")
                            .tag("outcome", "success")
                            .counter();

                    assertThat(counter)
                            .isNotNull()
                            .extracting(Counter::count)
                            .isEqualTo(3.0);
                });

        await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    Timer timer = registry.find("guardrail.timed")
                            .tag("aiservice", MyAiService.class.getName())
                            .tag("operation", "chatWithSuccessGuardrail")
                            .tag("guardrail", SuccessOutputGuardrail.class.getName())
                            .tag("guardrail.type", "output")
                            .tag("outcome", "success")
                            .timer();

                    assertThat(timer)
                            .isNotNull()
                            .extracting(Timer::count)
                            .isEqualTo(3L);
                });
    }

    @Test
    @ActivateRequestContext
    void shouldRecordMetricsForEachGuardrailInChain() {
        String result = aiService.chatWithChainedGuardrails("hello");
        assertThat(result).isEqualTo("response");

        await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    Counter counter1 = registry.find("guardrail.invoked")
                            .tag("aiservice", MyAiService.class.getName())
                            .tag("operation", "chatWithChainedGuardrails")
                            .tag("guardrail", FirstOutputGuardrail.class.getName())
                            .tag("guardrail.type", "output")
                            .tag("outcome", "success")
                            .counter();

                    assertThat(counter1)
                            .isNotNull()
                            .extracting(Counter::count)
                            .isEqualTo(1.0);

                    Timer timer1 = registry.find("guardrail.timed")
                            .tag("aiservice", MyAiService.class.getName())
                            .tag("operation", "chatWithChainedGuardrails")
                            .tag("guardrail", FirstOutputGuardrail.class.getName())
                            .tag("guardrail.type", "output")
                            .tag("outcome", "success")
                            .timer();

                    assertThat(timer1)
                            .isNotNull()
                            .extracting(Timer::count)
                            .isEqualTo(1L);
                });

        await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    Counter counter2 = registry.find("guardrail.invoked")
                            .tag("aiservice", MyAiService.class.getName())
                            .tag("operation", "chatWithChainedGuardrails")
                            .tag("guardrail", SecondOutputGuardrail.class.getName())
                            .tag("guardrail.type", "output")
                            .tag("outcome", "success")
                            .counter();

                    assertThat(counter2)
                            .isNotNull()
                            .extracting(Counter::count)
                            .isEqualTo(1.0);

                    Timer timer2 = registry.find("guardrail.timed")
                            .tag("aiservice", MyAiService.class.getName())
                            .tag("operation", "chatWithChainedGuardrails")
                            .tag("guardrail", SecondOutputGuardrail.class.getName())
                            .tag("guardrail.type", "output")
                            .tag("outcome", "success")
                            .timer();

                    assertThat(timer2)
                            .isNotNull()
                            .extracting(Timer::count)
                            .isEqualTo(1L);
                });
    }

    @Test
    @ActivateRequestContext
    void shouldRecordMetricsWhenGuardrailFailsInChain() {
        assertThatThrownBy(() -> aiService.chatWithChainedGuardrailsWithFailure("hello"))
                .isInstanceOf(GuardrailException.class)
                .hasMessageContaining("Second guardrail failed");

        await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    Counter counter1 = registry.find("guardrail.invoked")
                            .tag("aiservice", MyAiService.class.getName())
                            .tag("operation", "chatWithChainedGuardrailsWithFailure")
                            .tag("guardrail", FirstOutputGuardrail.class.getName())
                            .tag("guardrail.type", "output")
                            .tag("outcome", "success")
                            .counter();

                    assertThat(counter1)
                            .isNotNull()
                            .extracting(Counter::count)
                            .isEqualTo(1.0);
                });

        await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    Counter counter2 = registry.find("guardrail.invoked")
                            .tag("aiservice", MyAiService.class.getName())
                            .tag("operation", "chatWithChainedGuardrailsWithFailure")
                            .tag("guardrail", SecondOutputGuardrailWithFailure.class.getName())
                            .tag("guardrail.type", "output")
                            .tag("outcome", "failure")
                            .counter();

                    assertThat(counter2)
                            .isNotNull()
                            .extracting(Counter::count)
                            .isEqualTo(1.0);
                });
    }

    @Test
    @ActivateRequestContext
    void shouldRecordOutputGuardrailMetricsOnStreamingFullyAccumulated() {
        aiService.chatWithStreamingSuccessGuardrailFullyAccumulated("hello")
                .collect().asList()
                .await().indefinitely();

        await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    Counter counter = registry.find("guardrail.invoked")
                            .tag("aiservice", MyAiService.class.getName())
                            .tag("operation", "chatWithStreamingSuccessGuardrailFullyAccumulated")
                            .tag("guardrail", StreamingSuccessOutputGuardrail.class.getName())
                            .tag("guardrail.type", "output")
                            .tag("outcome", "success")
                            .counter();

                    assertThat(counter)
                            .isNotNull()
                            .extracting(Counter::count)
                            .isEqualTo(1.0);
                });

        await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    Timer timer = registry.find("guardrail.timed")
                            .tag("aiservice", MyAiService.class.getName())
                            .tag("operation", "chatWithStreamingSuccessGuardrailFullyAccumulated")
                            .tag("guardrail", StreamingSuccessOutputGuardrail.class.getName())
                            .tag("guardrail.type", "output")
                            .tag("outcome", "success")
                            .timer();

                    assertThat(timer)
                            .isNotNull()
                            .extracting(Timer::count)
                            .isEqualTo(1L);

                    assertThat(timer.totalTime(TimeUnit.NANOSECONDS))
                            .isGreaterThan(0);
                });
    }

    @Test
    @ActivateRequestContext
    void shouldRecordOutputGuardrailMetricsOnStreamingPartiallyAccumulated() {
        aiService.chatWithStreamingSuccessGuardrailPartiallyAccumulated("hello")
                .collect().asList()
                .await().indefinitely();

        // With PassThrough accumulator, the guardrail is invoked for each chunk (3 times)
        await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    Counter counter = registry.find("guardrail.invoked")
                            .tag("aiservice", MyAiService.class.getName())
                            .tag("operation", "chatWithStreamingSuccessGuardrailPartiallyAccumulated")
                            .tag("guardrail", StreamingSuccessOutputGuardrail.class.getName())
                            .tag("guardrail.type", "output")
                            .tag("outcome", "success")
                            .counter();

                    assertThat(counter)
                            .isNotNull()
                            .extracting(Counter::count)
                            .isEqualTo(3.0);
                });

        await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    Timer timer = registry.find("guardrail.timed")
                            .tag("aiservice", MyAiService.class.getName())
                            .tag("operation", "chatWithStreamingSuccessGuardrailPartiallyAccumulated")
                            .tag("guardrail", StreamingSuccessOutputGuardrail.class.getName())
                            .tag("guardrail.type", "output")
                            .tag("outcome", "success")
                            .timer();

                    assertThat(timer)
                            .isNotNull()
                            .extracting(Timer::count)
                            .isEqualTo(3L);

                    assertThat(timer.totalTime(TimeUnit.NANOSECONDS))
                            .isGreaterThan(0);
                });
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, streamingChatLanguageModelSupplier = MyStreamingChatModelSupplier.class)
    public interface MyAiService {

        @OutputGuardrails(SuccessOutputGuardrail.class)
        String chatWithSuccessGuardrail(String message);

        @OutputGuardrails(FailureOutputGuardrail.class)
        String chatWithFailureGuardrail(String message);

        @OutputGuardrails(RepromptOutputGuardrail.class)
        String chatWithRepromptGuardrail(String message);

        @OutputGuardrails({ FirstOutputGuardrail.class, SecondOutputGuardrail.class })
        String chatWithChainedGuardrails(String message);

        @OutputGuardrails({ FirstOutputGuardrail.class, SecondOutputGuardrailWithFailure.class })
        String chatWithChainedGuardrailsWithFailure(String message);

        @OutputGuardrails(StreamingSuccessOutputGuardrail.class)
        Multi<String> chatWithStreamingSuccessGuardrailFullyAccumulated(String message);

        @OutputGuardrails(StreamingSuccessOutputGuardrail.class)
        @OutputGuardrailAccumulator(PassThroughAccumulator.class)
        Multi<String> chatWithStreamingSuccessGuardrailPartiallyAccumulated(String message);
    }

    @ApplicationScoped
    public static class SuccessOutputGuardrail implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            return success();
        }
    }

    @ApplicationScoped
    public static class FailureOutputGuardrail implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            return failure("Output response is invalid");
        }
    }

    @ApplicationScoped
    public static class RepromptOutputGuardrail implements OutputGuardrail {
        private final AtomicInteger invocationCount = new AtomicInteger(0);

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            int count = invocationCount.incrementAndGet();
            if (count == 1) {
                // First invocation: request reprompt
                return reprompt("Please retry", "Response needs improvement");
            }
            // Second invocation: success
            return success();
        }

        public int getInvocationCount() {
            return invocationCount.get();
        }

        public void reset() {
            invocationCount.set(0);
        }
    }

    @ApplicationScoped
    public static class FirstOutputGuardrail implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            return success();
        }
    }

    @ApplicationScoped
    public static class SecondOutputGuardrail implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            return success();
        }
    }

    @ApplicationScoped
    public static class SecondOutputGuardrailWithFailure implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            return failure("Second guardrail failed");
        }
    }

    public static class MyChatModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new MyChatModel();
        }
    }

    public static class MyChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest request) {
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("response"))
                    .build();
        }
    }

    @ApplicationScoped
    public static class StreamingSuccessOutputGuardrail implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            return success();
        }
    }

    public static class MyStreamingChatModelSupplier implements Supplier<StreamingChatModel> {
        @Override
        public StreamingChatModel get() {
            return new MyStreamingChatModel();
        }
    }

    public static class MyStreamingChatModel implements StreamingChatModel {
        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            handler.onPartialResponse("Hello");
            handler.onPartialResponse(" ");
            handler.onPartialResponse("World!");
            handler.onCompleteResponse(ChatResponse.builder().aiMessage(new AiMessage("")).build());
        }
    }

    @ApplicationScoped
    public static class PassThroughAccumulator implements OutputTokenAccumulator {
        @Override
        public Multi<String> accumulate(Multi<String> tokens) {
            return tokens;
        }
    }

    @ApplicationScoped
    public static class MeterRegistryProducer {
        @Produces
        @ApplicationScoped
        public MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
