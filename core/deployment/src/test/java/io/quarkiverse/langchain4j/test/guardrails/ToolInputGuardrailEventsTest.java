package io.quarkiverse.langchain4j.test.guardrails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.event.Observes;
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
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkiverse.langchain4j.guardrails.ToolGuardrailException;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrail;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrailRequest;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrailResult;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrails;
import io.quarkiverse.langchain4j.runtime.aiservice.NoopChatMemory;
import io.quarkiverse.langchain4j.runtime.observability.ToolGuardrailOutcome;
import io.quarkiverse.langchain4j.runtime.observability.ToolInputGuardrailExecutedEvent;
import io.quarkiverse.langchain4j.test.Lists;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Tests that ToolInputGuardrailExecutedEvent is fired with correct metadata.
 */
public class ToolInputGuardrailEventsTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            MyAiService.class,
                            SuccessGuardrail.class,
                            FailureGuardrail.class,
                            FatalGuardrail.class,
                            EventCollector.class,
                            Lists.class));

    @Inject
    MyAiService aiService;

    @Inject
    MyTools tools;

    @Inject
    EventCollector eventCollector;

    @BeforeEach
    void setUp() {
        SuccessGuardrail.reset();
        FailureGuardrail.reset();
        FatalGuardrail.reset();
        tools.reset();
        eventCollector.reset();
    }

    @Test
    @ActivateRequestContext
    void testSuccessEvent() {
        String result = aiService.chat("test-memory-id", "successTool - hello");

        // Tool should have been executed
        assertThat(tools.successToolExecuted).isTrue();

        // Verify event was fired
        assertThat(eventCollector.inputEvents).hasSize(1);
        ToolInputGuardrailExecutedEvent event = eventCollector.inputEvents.get(0);

        // Verify all metadata
        assertThat(event.toolClass()).isEqualTo(SuccessGuardrail.class);
        assertThat(event.toolName()).isEqualTo("successTool");
        assertThat(event.outcome()).isEqualTo(ToolGuardrailOutcome.SUCCESS);
        assertThat(event.duration()).isGreaterThan(0);

        // Verify tool invocation context
        assertThat(event.toolInvocationContext()).isNotNull();
        assertThat(event.toolInvocationContext().memoryId()).isEqualTo("test-memory-id");

        assertThat(event.toolInvocationContext().context().interfaceName()).isEqualTo(MyAiService.class.getName());
        assertThat(event.toolInvocationContext().context().methodName()).isEqualTo("chat");
    }

    @Test
    @ActivateRequestContext
    void testFailureEvent() {
        String result = aiService.chat("test-memory-id", "failureTool - invalid");

        // Tool should NOT have been executed (guardrail failed)
        assertThat(tools.failureToolExecuted).isFalse();

        // Verify event was fired
        assertThat(eventCollector.inputEvents).hasSize(1);
        ToolInputGuardrailExecutedEvent event = eventCollector.inputEvents.get(0);

        // Verify metadata
        assertThat(event.toolClass()).isEqualTo(FailureGuardrail.class);
        assertThat(event.toolName()).isEqualTo("failureTool");
        assertThat(event.outcome()).isEqualTo(ToolGuardrailOutcome.FAILURE);
        assertThat(event.duration()).isGreaterThan(0);
        assertThat(event.toolInvocationContext()).isNotNull();
        assertThat(event.toolInvocationContext().memoryId()).isEqualTo("test-memory-id");

        assertThat(event.toolInvocationContext().context().interfaceName()).isEqualTo(MyAiService.class.getName());
        assertThat(event.toolInvocationContext().context().methodName()).isEqualTo("chat");
    }

    @Test
    @ActivateRequestContext
    void testFatalEvent() {
        assertThatThrownBy(() -> aiService.chat("test-memory-id", "fatalTool - anything"))
                .isInstanceOf(ToolGuardrailException.class)
                .hasMessageContaining("Fatal error");

        // Tool should NOT have been executed (guardrail failed fatally)
        assertThat(tools.fatalToolExecuted).isFalse();

        // Verify event was fired
        assertThat(eventCollector.inputEvents).hasSize(1);
        ToolInputGuardrailExecutedEvent event = eventCollector.inputEvents.get(0);

        // Verify metadata
        assertThat(event.toolClass()).isEqualTo(FatalGuardrail.class);
        assertThat(event.toolName()).isEqualTo("fatalTool");
        assertThat(event.outcome()).isEqualTo(ToolGuardrailOutcome.FATAL);
        assertThat(event.duration()).isGreaterThan(0);
        assertThat(event.toolInvocationContext()).isNotNull();
        assertThat(event.toolInvocationContext().memoryId()).isEqualTo("test-memory-id");

        assertThat(event.toolInvocationContext().context().interfaceName()).isEqualTo(MyAiService.class.getName());
        assertThat(event.toolInvocationContext().context().methodName()).isEqualTo("chat");
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
        @ToolInputGuardrails({ SuccessGuardrail.class })
        public String successTool(String input) {
            successToolExecuted = true;
            return "Success: " + input;
        }

        @Tool
        @ToolInputGuardrails({ FailureGuardrail.class })
        public String failureTool(String input) {
            failureToolExecuted = true;
            return "Should not execute";
        }

        @Tool
        @ToolInputGuardrails({ FatalGuardrail.class })
        public String fatalTool(String input) {
            fatalToolExecuted = true;
            return "Should not execute";
        }

        void reset() {
            successToolExecuted = false;
            failureToolExecuted = false;
            fatalToolExecuted = false;
        }
    }

    // Guardrails
    @ApplicationScoped
    public static class SuccessGuardrail implements ToolInputGuardrail {
        static int executionCount = 0;

        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            executionCount++;
            return ToolInputGuardrailResult.success();
        }

        static void reset() {
            executionCount = 0;
        }
    }

    @ApplicationScoped
    public static class FailureGuardrail implements ToolInputGuardrail {
        static int executionCount = 0;

        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            executionCount++;
            return ToolInputGuardrailResult.failure("Validation failed");
        }

        static void reset() {
            executionCount = 0;
        }
    }

    @ApplicationScoped
    public static class FatalGuardrail implements ToolInputGuardrail {
        static int executionCount = 0;

        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            executionCount++;
            return ToolInputGuardrailResult.fatal("Fatal error", new SecurityException("Unauthorized"));
        }

        static void reset() {
            executionCount = 0;
        }
    }

    @Singleton
    public static class EventCollector {
        List<ToolInputGuardrailExecutedEvent> inputEvents = new ArrayList<>();

        public void onInputGuardrailExecuted(@Observes ToolInputGuardrailExecutedEvent event) {
            inputEvents.add(event);
        }

        void reset() {
            inputEvents.clear();
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
