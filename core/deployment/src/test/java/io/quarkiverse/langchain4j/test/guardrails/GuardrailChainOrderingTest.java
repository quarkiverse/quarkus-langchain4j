package io.quarkiverse.langchain4j.test.guardrails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
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
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkiverse.langchain4j.guardrails.ToolGuardrailException;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrail;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrailRequest;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrailResult;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrails;
import io.quarkiverse.langchain4j.guardrails.ToolOutputGuardrail;
import io.quarkiverse.langchain4j.guardrails.ToolOutputGuardrailRequest;
import io.quarkiverse.langchain4j.guardrails.ToolOutputGuardrailResult;
import io.quarkiverse.langchain4j.guardrails.ToolOutputGuardrails;
import io.quarkiverse.langchain4j.runtime.aiservice.NoopChatMemory;
import io.quarkiverse.langchain4j.test.Lists;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Tests for guardrail chain execution and ordering.
 * Tests the sequential execution, fail-fast semantics, and request/result propagation.
 */
public class GuardrailChainOrderingTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            MyAiService.class,
                            InputGuardrail1.class,
                            InputGuardrail2.class,
                            InputGuardrail3.class,
                            InputGuardrail4.class,
                            InputGuardrail5.class,
                            OutputGuardrail1.class,
                            OutputGuardrail2.class,
                            OutputGuardrail3.class,
                            OutputGuardrail4.class,
                            OutputGuardrail5.class,
                            Lists.class));

    @Inject
    MyAiService aiService;

    @Inject
    MyTools tools;

    @BeforeEach
    void setUp() {
        ExecutionTracker.reset();
        tools.reset();
    }

    @Test
    @ActivateRequestContext
    void testInputChain_executesInOrder() {
        aiService.chat("test", "inputChain5 - hello");

        // Verify all 5 guardrails executed in the correct order
        List<String> executions = ExecutionTracker.getInputExecutions();
        assertThat(executions).containsExactly(
                "InputGuardrail1",
                "InputGuardrail2",
                "InputGuardrail3",
                "InputGuardrail4",
                "InputGuardrail5");

        // Verify they executed sequentially with increasing timestamps
        List<Long> timestamps = ExecutionTracker.getInputTimestamps();
        assertThat(timestamps).hasSize(5);
        for (int i = 1; i < timestamps.size(); i++) {
            assertThat(timestamps.get(i)).isGreaterThan(timestamps.get(i - 1));
        }

        // Tool should have been executed after all guardrails
        assertThat(tools.inputChain5Executed).isTrue();
    }

    @Test
    @ActivateRequestContext
    void testInputChain_propagatesModifications() {
        aiService.chat("test", "inputChain5 - transform");

        // InputGuardrail1 adds "[G1]"
        // InputGuardrail2 adds "[G2]"
        // InputGuardrail3 adds "[G3]"
        // InputGuardrail4 adds "[G4]"
        // InputGuardrail5 adds "[G5]"
        // Final result should have all transformations
        assertThat(tools.lastInput).isEqualTo("transform[G1][G2][G3][G4][G5]");
    }

    @Test
    @ActivateRequestContext
    void testInputChain_stopsOnFirstFailure() {
        // InputGuardrail2 will fail when input contains "fail-at-2"
        String result = aiService.chat("test", "inputChain5 - fail-at-2");

        // Only first 2 guardrails should have executed
        List<String> executions = ExecutionTracker.getInputExecutions();
        assertThat(executions).containsExactly(
                "InputGuardrail1",
                "InputGuardrail2");

        // Guardrails 3, 4, 5 should NOT have executed
        assertThat(executions).doesNotContain(
                "InputGuardrail3",
                "InputGuardrail4",
                "InputGuardrail5");

        // Tool should NOT have been executed
        assertThat(tools.inputChain5Executed).isFalse();

        // Result should contain the error message
        assertThat(result).contains("Failed at guardrail 2");
    }

    @Test
    @ActivateRequestContext
    void testInputChain_stopsOnMiddleFailure() {
        // InputGuardrail4 will fail when input contains "fail-at-4"
        String result = aiService.chat("test", "inputChain5 - fail-at-4");

        // First 4 guardrails should have executed
        List<String> executions = ExecutionTracker.getInputExecutions();
        assertThat(executions).containsExactly(
                "InputGuardrail1",
                "InputGuardrail2",
                "InputGuardrail3",
                "InputGuardrail4");

        // Guardrail 5 should NOT have executed
        assertThat(executions).doesNotContain("InputGuardrail5");

        // Tool should NOT have been executed
        assertThat(tools.inputChain5Executed).isFalse();
    }

    @Test
    @ActivateRequestContext
    void testInputChain_fatalFailureStopsImmediately() {
        // InputGuardrail3 will fail fatally when input contains "fatal-at-3"
        assertThatThrownBy(() -> aiService.chat("test", "inputChain5 - fatal-at-3"))
                .isInstanceOf(ToolGuardrailException.class)
                .hasMessageContaining("Fatal failure at guardrail 3")
                .hasCauseInstanceOf(SecurityException.class);

        // Only first 3 guardrails should have executed
        List<String> executions = ExecutionTracker.getInputExecutions();
        assertThat(executions).containsExactly(
                "InputGuardrail1",
                "InputGuardrail2",
                "InputGuardrail3");

        // Tool should NOT have been executed
        assertThat(tools.inputChain5Executed).isFalse();
    }

    @Test
    @ActivateRequestContext
    void testOutputChain_executesInOrder() {
        aiService.chat("test", "outputChain5 - hello");

        // Verify all 5 guardrails executed in the correct order
        List<String> executions = ExecutionTracker.getOutputExecutions();
        assertThat(executions).containsExactly(
                "OutputGuardrail1",
                "OutputGuardrail2",
                "OutputGuardrail3",
                "OutputGuardrail4",
                "OutputGuardrail5");

        // Verify they executed sequentially with increasing timestamps
        List<Long> timestamps = ExecutionTracker.getOutputTimestamps();
        assertThat(timestamps).hasSize(5);
        for (int i = 1; i < timestamps.size(); i++) {
            assertThat(timestamps.get(i)).isGreaterThan(timestamps.get(i - 1));
        }

        // Tool should have been executed before all guardrails
        assertThat(tools.outputChain5Executed).isTrue();
    }

    @Test
    @ActivateRequestContext
    void testOutputChain_propagatesModifications() {
        String result = aiService.chat("test", "outputChain5 - transform");

        // OutputGuardrail1 adds "[G1]"
        // OutputGuardrail2 adds "[G2]"
        // OutputGuardrail3 adds "[G3]"
        // OutputGuardrail4 adds "[G4]"
        // OutputGuardrail5 adds "[G5]"
        // Final result should have all transformations
        // (Mock LLM wraps with "response: " prefix and quotes)
        assertThat(result).contains("[G1][G2][G3][G4][G5]");
    }

    @Test
    @ActivateRequestContext
    void testOutputChain_stopsOnFirstFailure() {
        // OutputGuardrail2 will fail when result contains "fail-at-2"
        String result = aiService.chat("test", "outputChain5 - fail-at-2");

        // Only first 2 guardrails should have executed
        List<String> executions = ExecutionTracker.getOutputExecutions();
        assertThat(executions).containsExactly(
                "OutputGuardrail1",
                "OutputGuardrail2");

        // Guardrails 3, 4, 5 should NOT have executed
        assertThat(executions).doesNotContain(
                "OutputGuardrail3",
                "OutputGuardrail4",
                "OutputGuardrail5");

        // Tool SHOULD have been executed (output guardrails run after tool)
        assertThat(tools.outputChain5Executed).isTrue();

        // Result should contain the error message
        assertThat(result).contains("Failed at guardrail 2");
    }

    @Test
    @ActivateRequestContext
    void testOutputChain_stopsOnMiddleFailure() {
        // OutputGuardrail4 will fail when result contains "fail-at-4"
        String result = aiService.chat("test", "outputChain5 - fail-at-4");

        // First 4 guardrails should have executed
        List<String> executions = ExecutionTracker.getOutputExecutions();
        assertThat(executions).containsExactly(
                "OutputGuardrail1",
                "OutputGuardrail2",
                "OutputGuardrail3",
                "OutputGuardrail4");

        // Guardrail 5 should NOT have executed
        assertThat(executions).doesNotContain("OutputGuardrail5");

        // Tool SHOULD have been executed
        assertThat(tools.outputChain5Executed).isTrue();
    }

    @Test
    @ActivateRequestContext
    void testOutputChain_fatalFailureStopsImmediately() {
        // OutputGuardrail3 will fail fatally when result contains "fatal-at-3"
        assertThatThrownBy(() -> aiService.chat("test", "outputChain5 - fatal-at-3"))
                .isInstanceOf(ToolGuardrailException.class)
                .hasMessageContaining("Fatal failure at guardrail 3")
                .hasCauseInstanceOf(SecurityException.class);

        // Only first 3 guardrails should have executed
        List<String> executions = ExecutionTracker.getOutputExecutions();
        assertThat(executions).containsExactly(
                "OutputGuardrail1",
                "OutputGuardrail2",
                "OutputGuardrail3");

        // Tool SHOULD have been executed (output guardrails run after tool)
        assertThat(tools.outputChain5Executed).isTrue();
    }

    @Test
    @ActivateRequestContext
    void testMixedChain_inputThenOutput() {
        aiService.chat("test", "mixedChain - hello");

        // All input guardrails should execute first
        List<String> inputExecutions = ExecutionTracker.getInputExecutions();
        assertThat(inputExecutions).containsExactly(
                "InputGuardrail1",
                "InputGuardrail2");

        // Then the tool executes
        assertThat(tools.mixedChainExecuted).isTrue();

        // Then all output guardrails should execute
        List<String> outputExecutions = ExecutionTracker.getOutputExecutions();
        assertThat(outputExecutions).containsExactly(
                "OutputGuardrail1",
                "OutputGuardrail2");

        // Input guardrails should complete before output guardrails start
        long lastInputTimestamp = ExecutionTracker.getInputTimestamps().get(
                ExecutionTracker.getInputTimestamps().size() - 1);
        long firstOutputTimestamp = ExecutionTracker.getOutputTimestamps().get(0);
        assertThat(firstOutputTimestamp).isGreaterThan(lastInputTimestamp);
    }

    @Test
    @ActivateRequestContext
    void testMixedChain_inputFailureSkipsOutput() {
        // InputGuardrail2 will fail
        String result = aiService.chat("test", "mixedChain - fail-input");

        // Input guardrails should have executed
        List<String> inputExecutions = ExecutionTracker.getInputExecutions();
        assertThat(inputExecutions).hasSize(2);

        // Tool should NOT have executed
        assertThat(tools.mixedChainExecuted).isFalse();

        // Output guardrails should NOT have executed
        List<String> outputExecutions = ExecutionTracker.getOutputExecutions();
        assertThat(outputExecutions).isEmpty();
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface MyAiService {
        @ToolBox(MyTools.class)
        String chat(@MemoryId String memoryId, @UserMessage String userMessage);
    }

    @Singleton
    public static class MyTools {
        boolean inputChain5Executed = false;
        boolean outputChain5Executed = false;
        boolean mixedChainExecuted = false;
        String lastInput = null;

        @Tool
        @ToolInputGuardrails({
                InputGuardrail1.class,
                InputGuardrail2.class,
                InputGuardrail3.class,
                InputGuardrail4.class,
                InputGuardrail5.class
        })
        public String inputChain5(String input) {
            inputChain5Executed = true;
            lastInput = input;
            return "Result: " + input;
        }

        @Tool
        @ToolOutputGuardrails({
                OutputGuardrail1.class,
                OutputGuardrail2.class,
                OutputGuardrail3.class,
                OutputGuardrail4.class,
                OutputGuardrail5.class
        })
        public String outputChain5(String input) {
            outputChain5Executed = true;
            lastInput = input;
            return "Result: " + input;
        }

        @Tool
        @ToolInputGuardrails({ InputGuardrail1.class, InputGuardrail2.class })
        @ToolOutputGuardrails({ OutputGuardrail1.class, OutputGuardrail2.class })
        public String mixedChain(String input) {
            mixedChainExecuted = true;
            lastInput = input;
            return "Result: " + input;
        }

        void reset() {
            inputChain5Executed = false;
            outputChain5Executed = false;
            mixedChainExecuted = false;
            lastInput = null;
        }
    }

    public static class ExecutionTracker {
        private static final List<String> inputExecutions = new ArrayList<>();
        private static final List<Long> inputTimestamps = new ArrayList<>();
        private static final List<String> outputExecutions = new ArrayList<>();
        private static final List<Long> outputTimestamps = new ArrayList<>();

        public static void recordInput(String guardrailName) {
            inputExecutions.add(guardrailName);
            inputTimestamps.add(System.nanoTime());
        }

        public static void recordOutput(String guardrailName) {
            outputExecutions.add(guardrailName);
            outputTimestamps.add(System.nanoTime());
        }

        public static List<String> getInputExecutions() {
            return new ArrayList<>(inputExecutions);
        }

        public static List<Long> getInputTimestamps() {
            return new ArrayList<>(inputTimestamps);
        }

        public static List<String> getOutputExecutions() {
            return new ArrayList<>(outputExecutions);
        }

        public static List<Long> getOutputTimestamps() {
            return new ArrayList<>(outputTimestamps);
        }

        public static void reset() {
            inputExecutions.clear();
            inputTimestamps.clear();
            outputExecutions.clear();
            outputTimestamps.clear();
        }
    }

    @ApplicationScoped
    public static class InputGuardrail1 implements ToolInputGuardrail {
        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            ExecutionTracker.recordInput("InputGuardrail1");

            if (request.arguments().contains("transform")) {
                ToolExecutionRequest modified = ToolExecutionRequest.builder()
                        .id(request.executionRequest().id())
                        .name(request.executionRequest().name())
                        .arguments(request.arguments().replace("\"transform\"", "\"transform[G1]\""))
                        .build();
                return ToolInputGuardrailResult.successWith(modified);
            }

            return ToolInputGuardrailResult.success();
        }
    }

    @ApplicationScoped
    public static class InputGuardrail2 implements ToolInputGuardrail {
        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            ExecutionTracker.recordInput("InputGuardrail2");

            if (request.arguments().contains("fail-at-2")) {
                return ToolInputGuardrailResult.failure("Failed at guardrail 2");
            }

            if (request.arguments().contains("fail-input")) {
                return ToolInputGuardrailResult.failure("Input validation failed");
            }

            if (request.arguments().contains("transform")) {
                String args = request.arguments();
                String modified = args.replace("[G1]\"", "[G1][G2]\"");
                ToolExecutionRequest modifiedRequest = ToolExecutionRequest.builder()
                        .id(request.executionRequest().id())
                        .name(request.executionRequest().name())
                        .arguments(modified)
                        .build();
                return ToolInputGuardrailResult.successWith(modifiedRequest);
            }

            return ToolInputGuardrailResult.success();
        }
    }

    @ApplicationScoped
    public static class InputGuardrail3 implements ToolInputGuardrail {
        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            ExecutionTracker.recordInput("InputGuardrail3");

            if (request.arguments().contains("fatal-at-3")) {
                return ToolInputGuardrailResult.failure(
                        "Fatal failure at guardrail 3",
                        new SecurityException("Unauthorized"));
            }

            if (request.arguments().contains("transform")) {
                String args = request.arguments();
                String modified = args.replace("[G2]\"", "[G2][G3]\"");
                ToolExecutionRequest modifiedRequest = ToolExecutionRequest.builder()
                        .id(request.executionRequest().id())
                        .name(request.executionRequest().name())
                        .arguments(modified)
                        .build();
                return ToolInputGuardrailResult.successWith(modifiedRequest);
            }

            return ToolInputGuardrailResult.success();
        }
    }

    @ApplicationScoped
    public static class InputGuardrail4 implements ToolInputGuardrail {
        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            ExecutionTracker.recordInput("InputGuardrail4");

            if (request.arguments().contains("fail-at-4")) {
                return ToolInputGuardrailResult.failure("Failed at guardrail 4");
            }

            if (request.arguments().contains("transform")) {
                String args = request.arguments();
                String modified = args.replace("[G3]\"", "[G3][G4]\"");
                ToolExecutionRequest modifiedRequest = ToolExecutionRequest.builder()
                        .id(request.executionRequest().id())
                        .name(request.executionRequest().name())
                        .arguments(modified)
                        .build();
                return ToolInputGuardrailResult.successWith(modifiedRequest);
            }

            return ToolInputGuardrailResult.success();
        }
    }

    @ApplicationScoped
    public static class InputGuardrail5 implements ToolInputGuardrail {
        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            ExecutionTracker.recordInput("InputGuardrail5");

            if (request.arguments().contains("transform")) {
                String args = request.arguments();
                String modified = args.replace("[G4]\"", "[G4][G5]\"");
                ToolExecutionRequest modifiedRequest = ToolExecutionRequest.builder()
                        .id(request.executionRequest().id())
                        .name(request.executionRequest().name())
                        .arguments(modified)
                        .build();
                return ToolInputGuardrailResult.successWith(modifiedRequest);
            }

            return ToolInputGuardrailResult.success();
        }
    }

    @ApplicationScoped
    public static class OutputGuardrail1 implements ToolOutputGuardrail {
        @Override
        public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
            ExecutionTracker.recordOutput("OutputGuardrail1");

            if (request.resultText().contains("transform")) {
                ToolExecutionResult modified = ToolExecutionResult.builder()
                        .resultText(request.resultText() + "[G1]")
                        .build();
                return ToolOutputGuardrailResult.successWith(modified);
            }

            return ToolOutputGuardrailResult.success();
        }
    }

    @ApplicationScoped
    public static class OutputGuardrail2 implements ToolOutputGuardrail {
        @Override
        public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
            ExecutionTracker.recordOutput("OutputGuardrail2");

            if (request.resultText().contains("fail-at-2")) {
                return ToolOutputGuardrailResult.failure("Failed at guardrail 2");
            }

            if (request.resultText().contains("transform")) {
                ToolExecutionResult modified = ToolExecutionResult.builder()
                        .resultText(request.resultText() + "[G2]")
                        .build();
                return ToolOutputGuardrailResult.successWith(modified);
            }

            return ToolOutputGuardrailResult.success();
        }
    }

    @ApplicationScoped
    public static class OutputGuardrail3 implements ToolOutputGuardrail {
        @Override
        public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
            ExecutionTracker.recordOutput("OutputGuardrail3");

            if (request.resultText().contains("fatal-at-3")) {
                return ToolOutputGuardrailResult.failure(
                        "Fatal failure at guardrail 3",
                        new SecurityException("Data leak detected"));
            }

            if (request.resultText().contains("transform")) {
                ToolExecutionResult modified = ToolExecutionResult.builder()
                        .resultText(request.resultText() + "[G3]")
                        .build();
                return ToolOutputGuardrailResult.successWith(modified);
            }

            return ToolOutputGuardrailResult.success();
        }
    }

    @ApplicationScoped
    public static class OutputGuardrail4 implements ToolOutputGuardrail {
        @Override
        public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
            ExecutionTracker.recordOutput("OutputGuardrail4");

            if (request.resultText().contains("fail-at-4")) {
                return ToolOutputGuardrailResult.failure("Failed at guardrail 4");
            }

            if (request.resultText().contains("transform")) {
                ToolExecutionResult modified = ToolExecutionResult.builder()
                        .resultText(request.resultText() + "[G4]")
                        .build();
                return ToolOutputGuardrailResult.successWith(modified);
            }

            return ToolOutputGuardrailResult.success();
        }
    }

    @ApplicationScoped
    public static class OutputGuardrail5 implements ToolOutputGuardrail {
        @Override
        public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
            ExecutionTracker.recordOutput("OutputGuardrail5");

            if (request.resultText().contains("transform")) {
                ToolExecutionResult modified = ToolExecutionResult.builder()
                        .resultText(request.resultText() + "[G5]")
                        .build();
                return ToolOutputGuardrailResult.successWith(modified);
            }

            return ToolOutputGuardrailResult.success();
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
                String input = segments[1];

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
            return memoryId -> new NoopChatMemory();
        }
    }
}
