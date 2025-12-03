package io.quarkiverse.langchain4j.test.guardrails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkiverse.langchain4j.guardrails.ToolGuardrailException;
import io.quarkiverse.langchain4j.guardrails.ToolOutputGuardrail;
import io.quarkiverse.langchain4j.guardrails.ToolOutputGuardrailRequest;
import io.quarkiverse.langchain4j.guardrails.ToolOutputGuardrailResult;
import io.quarkiverse.langchain4j.guardrails.ToolOutputGuardrails;
import io.quarkiverse.langchain4j.runtime.aiservice.NoopChatMemory;
import io.quarkiverse.langchain4j.test.Lists;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Tests that tool output guardrail metrics are correctly recorded.
 */
public class ToolOutputGuardrailMetricsTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            MyAiService.class,
                            MyTools.class,
                            SuccessGuardrail.class,
                            FailureGuardrail.class,
                            FatalGuardrail.class,
                            MeterRegistryProducer.class,
                            Lists.class));

    @Inject
    MyAiService aiService;

    @Inject
    MyTools tools;

    @Inject
    MeterRegistry registry;

    @BeforeEach
    void setUp() {
        tools.reset();
        registry.clear();
    }

    @Test
    @ActivateRequestContext
    void shouldRecordSuccessMetrics() {
        String result = aiService.chat("test-memory-id", "successTool - hello");

        assertThat(tools.successToolExecuted).isTrue();

        // Verify counter metric
        await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    Counter counter = registry.find("tool-guardrail.invoked")
                            .tag("aiservice", MyAiService.class.getName())
                            .tag("operation", "chat")
                            .tag("tool.name", "successTool")
                            .tag("guardrail", SuccessGuardrail.class.getName())
                            .tag("guardrail.type", "output")
                            .tag("outcome", "success")
                            .counter();

                    assertThat(counter)
                            .as("Counter should be created for successful tool output guardrail")
                            .isNotNull()
                            .extracting(Counter::count)
                            .isEqualTo(1.0);
                });

        // Verify timer metric
        await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    Timer timer = registry.find("tool-guardrail.timed")
                            .tag("aiservice", MyAiService.class.getName())
                            .tag("operation", "chat")
                            .tag("tool.name", "successTool")
                            .tag("guardrail", SuccessGuardrail.class.getName())
                            .tag("guardrail.type", "output")
                            .tag("outcome", "success")
                            .timer();

                    assertThat(timer)
                            .as("Timer should be created for successful tool output guardrail")
                            .isNotNull()
                            .extracting(Timer::count)
                            .isEqualTo(1L);

                    assertThat(timer.totalTime(TimeUnit.NANOSECONDS))
                            .as("Timer should record duration")
                            .isGreaterThan(0);
                });
    }

    @Test
    @ActivateRequestContext
    void shouldRecordFailureMetrics() {
        String result = aiService.chat("test-memory-id", "failureTool - forbidden");

        // Tool SHOULD execute (output guardrails run after execution)
        assertThat(tools.failureToolExecuted).isTrue();

        // Verify counter metric
        await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    Counter counter = registry.find("tool-guardrail.invoked")
                            .tag("aiservice", MyAiService.class.getName())
                            .tag("operation", "chat")
                            .tag("tool.name", "failureTool")
                            .tag("guardrail", FailureGuardrail.class.getName())
                            .tag("guardrail.type", "output")
                            .tag("outcome", "failure")
                            .counter();

                    assertThat(counter)
                            .as("Counter should be created for failed tool output guardrail")
                            .isNotNull()
                            .extracting(Counter::count)
                            .isEqualTo(1.0);
                });

        // Verify timer metric
        await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    Timer timer = registry.find("tool-guardrail.timed")
                            .tag("aiservice", MyAiService.class.getName())
                            .tag("operation", "chat")
                            .tag("tool.name", "failureTool")
                            .tag("guardrail", FailureGuardrail.class.getName())
                            .tag("guardrail.type", "output")
                            .tag("outcome", "failure")
                            .timer();

                    assertThat(timer)
                            .as("Timer should be created for failed tool output guardrail")
                            .isNotNull()
                            .extracting(Timer::count)
                            .isEqualTo(1L);

                    assertThat(timer.totalTime(TimeUnit.NANOSECONDS))
                            .as("Timer should record duration even on failure")
                            .isGreaterThan(0);
                });
    }

    @Test
    @ActivateRequestContext
    void shouldRecordFatalMetrics() {
        assertThatThrownBy(() -> aiService.chat("test-memory-id", "fatalTool - anything"))
                .isInstanceOf(ToolGuardrailException.class)
                .hasMessageContaining("Fatal output error");

        // Tool SHOULD execute (output guardrails run after execution)
        assertThat(tools.fatalToolExecuted).isTrue();

        // Verify counter metric
        await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    Counter counter = registry.find("tool-guardrail.invoked")
                            .tag("aiservice", MyAiService.class.getName())
                            .tag("operation", "chat")
                            .tag("tool.name", "fatalTool")
                            .tag("guardrail", FatalGuardrail.class.getName())
                            .tag("guardrail.type", "output")
                            .tag("outcome", "fatal")
                            .counter();

                    assertThat(counter)
                            .as("Counter should be created for fatal tool output guardrail failure")
                            .isNotNull()
                            .extracting(Counter::count)
                            .isEqualTo(1.0);
                });

        // Verify timer metric
        await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    Timer timer = registry.find("tool-guardrail.timed")
                            .tag("aiservice", MyAiService.class.getName())
                            .tag("operation", "chat")
                            .tag("tool.name", "fatalTool")
                            .tag("guardrail", FatalGuardrail.class.getName())
                            .tag("guardrail.type", "output")
                            .tag("outcome", "fatal")
                            .timer();

                    assertThat(timer)
                            .as("Timer should be created for fatal tool output guardrail failure")
                            .isNotNull()
                            .extracting(Timer::count)
                            .isEqualTo(1L);

                    assertThat(timer.totalTime(TimeUnit.NANOSECONDS))
                            .as("Timer should record duration even on fatal failure")
                            .isGreaterThan(0);
                });
    }

    // AI Service
    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface MyAiService {
        @ToolBox(MyTools.class)
        String chat(@MemoryId String memoryId, @UserMessage String userMessage);
    }

    // Tools
    @Singleton
    public static class MyTools {
        boolean successToolExecuted = false;
        boolean failureToolExecuted = false;
        boolean fatalToolExecuted = false;

        @Tool
        @ToolOutputGuardrails({ SuccessGuardrail.class })
        public String successTool(String input) {
            successToolExecuted = true;
            return "Success: " + input;
        }

        @Tool
        @ToolOutputGuardrails({ FailureGuardrail.class })
        public String failureTool(String input) {
            failureToolExecuted = true;
            return "Contains forbidden word";
        }

        @Tool
        @ToolOutputGuardrails({ FatalGuardrail.class })
        public String fatalTool(String input) {
            fatalToolExecuted = true;
            return "Sensitive data";
        }

        void reset() {
            successToolExecuted = false;
            failureToolExecuted = false;
            fatalToolExecuted = false;
        }
    }

    // Guardrails
    @ApplicationScoped
    public static class SuccessGuardrail implements ToolOutputGuardrail {
        @Override
        public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
            return ToolOutputGuardrailResult.success();
        }
    }

    @ApplicationScoped
    public static class FailureGuardrail implements ToolOutputGuardrail {
        @Override
        public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
            if (request.resultText().contains("forbidden")) {
                return ToolOutputGuardrailResult.failure("Output validation failed");
            }
            return ToolOutputGuardrailResult.success();
        }
    }

    @ApplicationScoped
    public static class FatalGuardrail implements ToolOutputGuardrail {
        @Override
        public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
            return ToolOutputGuardrailResult.fatal("Fatal output error", new SecurityException("Data leak"));
        }
    }

    // Meter registry producer
    @ApplicationScoped
    public static class MeterRegistryProducer {
        @Produces
        @ApplicationScoped
        public MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    // Mock Chat Model
    public static class MyChatModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new MyChatModel();
        }
    }

    public static class MyChatModel implements ChatModel {
        @Override
        public ChatResponse chat(List<ChatMessage> messages) {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            List<ChatMessage> messages = chatRequest.messages();
            if (messages.size() == 1) {
                String text = ((dev.langchain4j.data.message.UserMessage) messages.get(0)).singleText();
                String[] segments = text.split(" - ");
                String toolName = segments[0];
                String input = segments.length > 1 ? segments[1] : "";

                return ChatResponse.builder()
                        .aiMessage(new AiMessage("executing tool", List.of(ToolExecutionRequest.builder()
                                .id("tool-id-1")
                                .name(toolName)
                                .arguments("{\"input\":\"" + input + "\"}")
                                .build())))
                        .tokenUsage(new TokenUsage(0, 0))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build();
            } else if (messages.size() == 3) {
                ToolExecutionResultMessage last = (ToolExecutionResultMessage) Lists.last(messages);
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from("response: " + last.text()))
                        .build();
            }
            return ChatResponse.builder()
                    .aiMessage(new AiMessage("Unexpected"))
                    .build();
        }
    }

    public static class MyMemoryProviderSupplier implements Supplier<ChatMemoryProvider> {
        @Override
        public ChatMemoryProvider get() {
            return new ChatMemoryProvider() {
                @Override
                public ChatMemory get(Object memoryId) {
                    return new NoopChatMemory();
                }
            };
        }
    }
}
