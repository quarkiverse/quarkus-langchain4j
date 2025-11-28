package io.quarkiverse.langchain4j.test.guardrails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
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
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailException;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.guardrail.InputGuardrails;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

public class InputGuardrailMetricsTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyAiService.class,
                            SuccessInputGuardrail.class,
                            FailureInputGuardrail.class,
                            FirstInputGuardrail.class,
                            SecondInputGuardrail.class,
                            SecondInputGuardrailWithFailure.class,
                            MyChatModel.class,
                            MyChatModelSupplier.class,
                            MeterRegistryProducer.class));

    @Inject
    MyAiService aiService;

    @Inject
    MeterRegistry registry;

    @BeforeEach
    void clearRegistry() {
        registry.clear();
    }

    @Test
    @ActivateRequestContext
    void shouldRecordInputGuardrailMetricsOnSuccess() {
        String result = aiService.chatWithSuccessGuardrail("hello");
        assertThat(result).isEqualTo("response");

        await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    Counter counter = registry.find("guardrail.invoked")
                            .tag("aiservice", MyAiService.class.getName())
                            .tag("operation", "chatWithSuccessGuardrail")
                            .tag("guardrail",
                                    SuccessInputGuardrail.class.getName())
                            .tag("guardrail.type", "input")
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
                            .tag("guardrail",
                                    SuccessInputGuardrail.class.getName())
                            .tag("guardrail.type", "input")
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
    void shouldRecordInputGuardrailMetricsOnFailure() {
        assertThatThrownBy(() -> aiService.chatWithFailureGuardrail("hello"))
                .isInstanceOf(InputGuardrailException.class)
                .hasMessageContaining("Input message is invalid");

        await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    Counter counter = registry.find("guardrail.invoked")
                            .tag("aiservice", MyAiService.class.getName())
                            .tag("operation", "chatWithFailureGuardrail")
                            .tag("guardrail",
                                    FailureInputGuardrail.class.getName())
                            .tag("guardrail.type", "input")
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
                            .tag("guardrail",
                                    FailureInputGuardrail.class.getName())
                            .tag("guardrail.type", "input")
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
                            .tag("guardrail",
                                    SuccessInputGuardrail.class.getName())
                            .tag("guardrail.type", "input")
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
                            .tag("guardrail",
                                    SuccessInputGuardrail.class.getName())
                            .tag("guardrail.type", "input")
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
                            .tag("guardrail",
                                    FirstInputGuardrail.class.getName())
                            .tag("guardrail.type", "input")
                            .tag("outcome", "success")
                            .counter();

                    assertThat(counter1)
                            .isNotNull()
                            .extracting(Counter::count)
                            .isEqualTo(1.0);

                    Timer timer1 = registry.find("guardrail.timed")
                            .tag("aiservice", MyAiService.class.getName())
                            .tag("operation", "chatWithChainedGuardrails")
                            .tag("guardrail",
                                    FirstInputGuardrail.class.getName())
                            .tag("guardrail.type", "input")
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
                            .tag("guardrail",
                                    SecondInputGuardrail.class.getName())
                            .tag("guardrail.type", "input")
                            .tag("outcome", "success")
                            .counter();

                    assertThat(counter2)
                            .isNotNull()
                            .extracting(Counter::count)
                            .isEqualTo(1.0);

                    Timer timer2 = registry.find("guardrail.timed")
                            .tag("aiservice", MyAiService.class.getName())
                            .tag("operation", "chatWithChainedGuardrails")
                            .tag("guardrail",
                                    SecondInputGuardrail.class.getName())
                            .tag("guardrail.type", "input")
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
                .isInstanceOf(InputGuardrailException.class)
                .hasMessageContaining("Second guardrail failed");

        await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    Counter counter1 = registry.find("guardrail.invoked")
                            .tag("aiservice", MyAiService.class.getName())
                            .tag("operation", "chatWithChainedGuardrailsWithFailure")
                            .tag("guardrail",
                                    FirstInputGuardrail.class.getName())
                            .tag("guardrail.type", "input")
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
                            .tag("guardrail",
                                    SecondInputGuardrailWithFailure.class.getName())
                            .tag("guardrail.type", "input")
                            .tag("outcome", "failure")
                            .counter();

                    assertThat(counter2)
                            .isNotNull()
                            .extracting(Counter::count)
                            .isEqualTo(1.0);
                });
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class)
    public interface MyAiService {

        @InputGuardrails(SuccessInputGuardrail.class)
        String chatWithSuccessGuardrail(String message);

        @InputGuardrails(FailureInputGuardrail.class)
        String chatWithFailureGuardrail(String message);

        @InputGuardrails({ FirstInputGuardrail.class, SecondInputGuardrail.class })
        String chatWithChainedGuardrails(String message);

        @InputGuardrails({ FirstInputGuardrail.class, SecondInputGuardrailWithFailure.class })
        String chatWithChainedGuardrailsWithFailure(String message);
    }

    @ApplicationScoped
    public static class SuccessInputGuardrail implements InputGuardrail {
        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            return success();
        }
    }

    @ApplicationScoped
    public static class FailureInputGuardrail implements InputGuardrail {
        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            return failure("Input message is invalid");
        }
    }

    @ApplicationScoped
    public static class FirstInputGuardrail implements InputGuardrail {
        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            return success();
        }
    }

    @ApplicationScoped
    public static class SecondInputGuardrail implements InputGuardrail {
        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            return success();
        }
    }

    @ApplicationScoped
    public static class SecondInputGuardrailWithFailure implements InputGuardrail {
        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
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
    public static class MeterRegistryProducer {
        @Produces
        @ApplicationScoped
        public MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
