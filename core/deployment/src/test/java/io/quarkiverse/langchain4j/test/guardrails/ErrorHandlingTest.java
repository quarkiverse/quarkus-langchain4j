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
 * Tests for error handling in tool guardrails.
 * Covers:
 * - Guardrails throwing unexpected exceptions
 * - Null handling in request/result builders
 * - Runtime error scenarios
 */
public class ErrorHandlingTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            MyAiService.class,
                            RuntimeExceptionGuardrail.class,
                            NullPointerExceptionGuardrail.class,
                            OutputRuntimeExceptionGuardrail.class,
                            Lists.class));

    @Inject
    MyAiService aiService;

    @Inject
    MyTools tools;

    @BeforeEach
    void setUp() {
        RuntimeExceptionGuardrail.reset();
        NullPointerExceptionGuardrail.reset();
        OutputRuntimeExceptionGuardrail.reset();
        tools.reset();
    }

    @Test
    @ActivateRequestContext
    void testInputGuardrail_throwsRuntimeException() {
        // When a guardrail throws an unexpected runtime exception,
        // it propagates directly (not wrapped)
        assertThatThrownBy(() -> aiService.chat("test", "runtimeExceptionTool - anything"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Unexpected error in guardrail");

        // Guardrail should have been executed
        assertThat(RuntimeExceptionGuardrail.executionCount).isEqualTo(1);

        // Tool should NOT have been executed
        assertThat(tools.runtimeExceptionToolExecuted).isFalse();
    }

    @Test
    @ActivateRequestContext
    void testInputGuardrail_throwsNullPointerException() {
        // NPE propagates directly
        assertThatThrownBy(() -> aiService.chat("test", "nullPointerTool - anything"))
                .isInstanceOf(NullPointerException.class);

        assertThat(NullPointerExceptionGuardrail.executionCount).isEqualTo(1);
        assertThat(tools.nullPointerToolExecuted).isFalse();
    }

    @Test
    @ActivateRequestContext
    void testInputGuardrail_recoversFromException() {
        // After an exception, subsequent calls should still work
        assertThatThrownBy(() -> aiService.chat("test", "runtimeExceptionTool - anything"))
                .isInstanceOf(RuntimeException.class);

        tools.reset();

        // This should work (different tool, no exception)
        var ignored = aiService.chat("test", "normalTool - data");
        assertThat(tools.normalToolExecuted).isTrue();
    }

    @Test
    @ActivateRequestContext
    void testOutputGuardrail_throwsRuntimeException() {
        // When an output guardrail throws an unexpected runtime exception,
        // it propagates directly
        assertThatThrownBy(() -> aiService.chat("test", "outputExceptionTool - anything"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Unexpected error in output guardrail");

        // Tool SHOULD have been executed (output guardrails run after tool)
        assertThat(tools.outputExceptionToolExecuted).isTrue();

        // Guardrail should have been executed
        assertThat(OutputRuntimeExceptionGuardrail.executionCount).isEqualTo(1);
    }

    @Test
    @ActivateRequestContext
    void testOutputGuardrail_recoversFromException() {
        // After an exception, subsequent calls should still work
        assertThatThrownBy(() -> aiService.chat("test", "outputExceptionTool - anything"))
                .isInstanceOf(RuntimeException.class);

        tools.reset();

        // This should work
        String result = aiService.chat("test", "normalTool - data");
        assertThat(tools.normalToolExecuted).isTrue();
    }

    @Test
    void testToolInputGuardrailRequest_allowsNullToolMetadata() {
        // Tool metadata can be null (optional context)
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("test-id")
                .name("test-tool")
                .arguments("{}")
                .build();

        // Records allow nulls, so this should work
        ToolInputGuardrailRequest guardrailRequest = new ToolInputGuardrailRequest(
                request,
                null, // toolMetadata
                null); // invocationContext

        assertThat(guardrailRequest.executionRequest()).isEqualTo(request);
        assertThat(guardrailRequest.toolMetadata()).isNull();
        assertThat(guardrailRequest.invocationContext()).isNull();
        assertThat(guardrailRequest.memoryId()).isNull();
    }

    @Test
    void testToolOutputGuardrailRequest_allowsNullExecutionRequest() {
        // executionRequest is optional for output guardrails
        dev.langchain4j.service.tool.ToolExecutionResult result = dev.langchain4j.service.tool.ToolExecutionResult.builder()
                .resultText("test result")
                .build();

        ToolOutputGuardrailRequest guardrailRequest = new ToolOutputGuardrailRequest(
                result,
                null, // executionRequest
                null, // toolMetadata
                null); // invocationContext

        assertThat(guardrailRequest.executionResult()).isEqualTo(result);
        assertThat(guardrailRequest.executionRequest()).isNull();
        assertThat(guardrailRequest.toolName()).isNull();
    }

    @Test
    void testToolInputGuardrailResult_nullMessage() {
        // Null error message should be allowed for success
        ToolInputGuardrailResult result = ToolInputGuardrailResult.success();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.errorMessage()).isNull();
        assertThat(result.cause()).isNull();
    }

    @Test
    void testToolOutputGuardrailResult_nullMessage() {
        // Null error message should be allowed for success
        ToolOutputGuardrailResult result = ToolOutputGuardrailResult.success();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.errorMessage()).isNull();
        assertThat(result.cause()).isNull();
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface MyAiService {
        @ToolBox(MyTools.class)
        String chat(@MemoryId String memoryId, @UserMessage String userMessage);
    }

    @Singleton
    public static class MyTools {
        boolean runtimeExceptionToolExecuted = false;
        boolean nullPointerToolExecuted = false;
        boolean outputExceptionToolExecuted = false;
        boolean normalToolExecuted = false;

        @Tool
        @ToolInputGuardrails({ RuntimeExceptionGuardrail.class })
        public String runtimeExceptionTool(String input) {
            runtimeExceptionToolExecuted = true;
            return "Result: " + input;
        }

        @Tool
        @ToolInputGuardrails({ NullPointerExceptionGuardrail.class })
        public String nullPointerTool(String input) {
            nullPointerToolExecuted = true;
            return "Result: " + input;
        }

        @Tool
        @ToolOutputGuardrails({ OutputRuntimeExceptionGuardrail.class })
        public String outputExceptionTool(String input) {
            outputExceptionToolExecuted = true;
            return "Result: " + input;
        }

        @Tool
        public String normalTool(String input) {
            normalToolExecuted = true;
            return "Normal result: " + input;
        }

        void reset() {
            runtimeExceptionToolExecuted = false;
            nullPointerToolExecuted = false;
            outputExceptionToolExecuted = false;
            normalToolExecuted = false;
        }
    }

    @ApplicationScoped
    public static class RuntimeExceptionGuardrail implements ToolInputGuardrail {
        static int executionCount = 0;

        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            executionCount++;
            throw new RuntimeException("Unexpected error in guardrail");
        }

        static void reset() {
            executionCount = 0;
        }
    }

    @ApplicationScoped
    public static class NullPointerExceptionGuardrail implements ToolInputGuardrail {
        static int executionCount = 0;

        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            executionCount++;
            // Simulate accidental NPE
            String nullString = null;
            nullString.length(); // This will throw NPE
            return ToolInputGuardrailResult.success();
        }

        static void reset() {
            executionCount = 0;
        }
    }

    @ApplicationScoped
    public static class OutputRuntimeExceptionGuardrail implements ToolOutputGuardrail {
        static int executionCount = 0;

        @Override
        public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
            executionCount++;
            throw new RuntimeException("Unexpected error in output guardrail");
        }

        static void reset() {
            executionCount = 0;
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
            return memoryId -> new NoopChatMemory();
        }
    }
}
