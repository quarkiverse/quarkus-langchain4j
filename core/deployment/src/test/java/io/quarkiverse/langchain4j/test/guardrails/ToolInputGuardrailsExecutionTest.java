package io.quarkiverse.langchain4j.test.guardrails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
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
import io.quarkiverse.langchain4j.test.Lists;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Test for tool input guardrails with AI Service execution.
 */
public class ToolInputGuardrailsExecutionTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            MyAiService.class,
                            ValidationGuardrail.class,
                            TransformGuardrail.class,
                            FailureGuardrail.class,
                            FatalFailureGuardrail.class,
                            FirstGuardrail.class,
                            SecondGuardrail.class,
                            Lists.class));

    @Inject
    MyAiService aiService;

    @Inject
    MyTools tools;

    @BeforeEach
    void setUp() {
        // Reset all state
        ValidationGuardrail.reset();
        TransformGuardrail.reset();
        FailureGuardrail.reset();
        FatalFailureGuardrail.reset();
        FirstGuardrail.reset();
        SecondGuardrail.reset();
        tools.reset();
    }

    @Test
    @ActivateRequestContext
    void testInputGuardrail_success() {
        String result = aiService.chat("test", "validatedTool - hello");

        // Guardrail should have been executed
        assertThat(ValidationGuardrail.executionCount).isEqualTo(1);
        assertThat(ValidationGuardrail.lastRequest).isNotNull();
        assertThat(ValidationGuardrail.lastRequest.toolName()).isEqualTo("validatedTool");

        // Tool should have been executed
        assertThat(tools.validatedToolExecuted).isTrue();
        assertThat(tools.lastInput).isEqualTo("hello");

        // Result should contain tool output
        assertThat(result).contains("Validated: hello");
    }

    @Test
    @ActivateRequestContext
    void testInputGuardrail_modifiesRequest() {
        String result = aiService.chat("test", "transformedTool - hello");

        // Guardrail should have been executed
        assertThat(TransformGuardrail.executionCount).isEqualTo(1);

        // Tool should have been executed with transformed input (uppercase)
        assertThat(tools.transformedToolExecuted).isTrue();
        assertThat(tools.lastInput).isEqualTo("HELLO"); // Transformed by guardrail

        // Result should contain transformed tool output
        assertThat(result).contains("Transformed: HELLO");
    }

    @Test
    @ActivateRequestContext
    void testInputGuardrail_failure() {
        // Guardrail will fail because input contains "invalid"
        // Non-fatal failure returns error result to LLM, not exception
        String result = aiService.chat("test", "failingTool - invalid");

        // Guardrail should have been executed
        assertThat(FailureGuardrail.executionCount).isEqualTo(1);

        // Tool should NOT have been executed
        assertThat(tools.failingToolExecuted).isFalse();

        // Result should contain the error message
        assertThat(result).contains("Input validation failed");
    }

    @Test
    @ActivateRequestContext
    void testInputGuardrail_fatalFailure() {
        // Guardrail will fail fatally
        assertThatThrownBy(() -> aiService.chat("test", "fatalTool - anything"))
                .isInstanceOf(ToolGuardrailException.class)
                .hasMessageContaining("Fatal validation error")
                .hasCauseInstanceOf(SecurityException.class);

        // Guardrail should have been executed
        assertThat(FatalFailureGuardrail.executionCount).isEqualTo(1);

        // Tool should NOT have been executed
        assertThat(tools.fatalToolExecuted).isFalse();
    }

    @Test
    @ActivateRequestContext
    void testInputGuardrails_chainExecution() {
        String result = aiService.chat("test", "chainedTool - hello");

        // Both guardrails should have been executed in order
        assertThat(FirstGuardrail.executionCount).isEqualTo(1);
        assertThat(SecondGuardrail.executionCount).isEqualTo(1);

        // Second guardrail should execute after first
        assertThat(SecondGuardrail.executionOrder).isGreaterThan(FirstGuardrail.executionOrder);

        // Tool should have been executed
        assertThat(tools.chainedToolExecuted).isTrue();

        // Result should contain tool output
        assertThat(result).contains("Chained: hello");
    }

    @Test
    @ActivateRequestContext
    void testInputGuardrails_chainStopsOnFailure() {
        // First guardrail will fail (non-fatal)
        // Non-fatal failure returns error result to LLM, not exception
        String result = aiService.chat("test", "chainedTool - fail-first");

        // First guardrail should have been executed
        assertThat(FirstGuardrail.executionCount).isEqualTo(1);

        // Second guardrail should NOT have been executed (fail-fast)
        assertThat(SecondGuardrail.executionCount).isEqualTo(0);

        // Tool should NOT have been executed
        assertThat(tools.chainedToolExecuted).isFalse();

        // Result should contain the error message
        assertThat(result).contains("First guardrail failed");
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
        boolean validatedToolExecuted = false;
        boolean transformedToolExecuted = false;
        boolean failingToolExecuted = false;
        boolean fatalToolExecuted = false;
        boolean chainedToolExecuted = false;
        String lastInput = null;

        @Tool
        @ToolInputGuardrails({ ValidationGuardrail.class })
        public String validatedTool(String input) {
            validatedToolExecuted = true;
            lastInput = input;
            return "Validated: " + input;
        }

        @Tool
        @ToolInputGuardrails({ TransformGuardrail.class })
        public String transformedTool(String input) {
            transformedToolExecuted = true;
            lastInput = input;
            return "Transformed: " + input;
        }

        @Tool
        @ToolInputGuardrails({ FailureGuardrail.class })
        public String failingTool(String input) {
            failingToolExecuted = true;
            lastInput = input;
            return "Should not execute";
        }

        @Tool
        @ToolInputGuardrails({ FatalFailureGuardrail.class })
        public String fatalTool(String input) {
            fatalToolExecuted = true;
            lastInput = input;
            return "Should not execute";
        }

        @Tool
        @ToolInputGuardrails({ FirstGuardrail.class, SecondGuardrail.class })
        public String chainedTool(String input) {
            chainedToolExecuted = true;
            lastInput = input;
            return "Chained: " + input;
        }

        void reset() {
            validatedToolExecuted = false;
            transformedToolExecuted = false;
            failingToolExecuted = false;
            fatalToolExecuted = false;
            chainedToolExecuted = false;
            lastInput = null;
        }
    }

    // Guardrails

    @ApplicationScoped
    public static class ValidationGuardrail implements ToolInputGuardrail {
        static int executionCount = 0;
        static ToolInputGuardrailRequest lastRequest = null;

        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            executionCount++;
            lastRequest = request;
            return ToolInputGuardrailResult.success();
        }

        static void reset() {
            executionCount = 0;
            lastRequest = null;
        }
    }

    @ApplicationScoped
    public static class TransformGuardrail implements ToolInputGuardrail {
        static int executionCount = 0;

        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            executionCount++;

            // Transform input to uppercase in JSON
            String args = request.arguments();
            String transformedArgs = args.replace("hello", "HELLO");

            ToolExecutionRequest modifiedRequest = ToolExecutionRequest.builder()
                    .id(request.executionRequest().id())
                    .name(request.executionRequest().name())
                    .arguments(transformedArgs)
                    .build();

            return ToolInputGuardrailResult.successWith(modifiedRequest);
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

            if (request.arguments().contains("invalid")) {
                return ToolInputGuardrailResult.failure("Input validation failed: contains 'invalid'");
            }

            return ToolInputGuardrailResult.success();
        }

        static void reset() {
            executionCount = 0;
        }
    }

    @ApplicationScoped
    public static class FatalFailureGuardrail implements ToolInputGuardrail {
        static int executionCount = 0;

        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            executionCount++;
            return ToolInputGuardrailResult.fatal(
                    "Fatal validation error",
                    new SecurityException("Unauthorized access"));
        }

        static void reset() {
            executionCount = 0;
        }
    }

    @ApplicationScoped
    public static class FirstGuardrail implements ToolInputGuardrail {
        static int executionCount = 0;
        static long executionOrder = 0;

        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            executionCount++;
            executionOrder = System.nanoTime();

            if (request.arguments().contains("fail-first")) {
                return ToolInputGuardrailResult.failure("First guardrail failed");
            }

            return ToolInputGuardrailResult.success();
        }

        static void reset() {
            executionCount = 0;
            executionOrder = 0;
        }
    }

    @ApplicationScoped
    public static class SecondGuardrail implements ToolInputGuardrail {
        static int executionCount = 0;
        static long executionOrder = 0;

        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            executionCount++;
            executionOrder = System.nanoTime();
            return ToolInputGuardrailResult.success();
        }

        static void reset() {
            executionCount = 0;
            executionOrder = 0;
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
                // Only the user message, extract tool name and arguments
                String text = ((dev.langchain4j.data.message.UserMessage) messages.get(0)).singleText();
                String[] segments = text.split(" - ");
                String toolName = segments[0];
                String input = segments[1];

                // Return tool execution request
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
                // user -> tool request -> tool response
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
