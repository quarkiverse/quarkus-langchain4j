package io.quarkiverse.langchain4j.test.guardrails;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

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
import dev.langchain4j.exception.ToolExecutionException;
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
import io.quarkiverse.langchain4j.runtime.BlockingToolNotAllowedException;
import io.quarkiverse.langchain4j.runtime.aiservice.NoopChatMemory;
import io.quarkiverse.langchain4j.test.Lists;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * Tests that guardrails CANNOT be executed on the Vert.x event loop.
 * <p>
 * This test verifies that:
 * <ul>
 * <li>Attempting to use tools with guardrails on the event loop throws ToolExecutionException</li>
 * <li>Guardrails work correctly when invoked from worker threads</li>
 * </ul>
 * <p>
 * The architectural decision is that tools with guardrails must be marked as BLOCKING,
 * because guardrails have synchronous APIs and cannot execute on the event loop.
 * </p>
 */
public class GuardrailEventLoopBlockingTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            MyAiService.class,
                            MyTools.class,
                            SimpleInputGuardrail.class,
                            SimpleOutputGuardrail.class,
                            Lists.class));

    @Inject
    Vertx vertx;

    @Inject
    MyAiService aiService;

    @Inject
    MyTools tools;

    @BeforeEach
    void setUp() {
        MyTools.reset();
        SimpleInputGuardrail.reset();
        SimpleOutputGuardrail.reset();
    }

    /**
     * CRITICAL TEST: Verifies that attempting to use guardrails on the event loop
     * throws ToolExecutionException.
     * <p>
     * This test actually runs the AI service call ON the event loop using vertx.runOnContext().
     * </p>
     */
    @Test
    void toolWithInputGuardrail_throwsException_whenCalledFromEventLoop() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> exceptionRef = new AtomicReference<>();

        // Run on event loop thread
        vertx.runOnContext(v -> {
            try {
                // Verify we're on event loop
                assertThat(io.vertx.core.Context.isOnEventLoopThread())
                        .as("Test setup: should be on event loop")
                        .isTrue();

                // This should throw ToolExecutionException because guardrails cannot run on event loop
                Arc.container().requestContext().activate();
                try {
                    aiService.chat("test", "toolWithInputGuardrail - hello");
                } finally {
                    Arc.container().requestContext().terminate();
                }
            } catch (Throwable e) {
                exceptionRef.set(e);
            } finally {
                latch.countDown();
            }
        });

        // Wait for event loop execution
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

        // Verify the expected exception was thrown
        Throwable exception = exceptionRef.get();
        assertThat(exception)
                .isNotNull()
                .isInstanceOf(BlockingToolNotAllowedException.class);

        // Our ToolGuardrailsWrapper should catch the event loop and throw a clear error
        assertThat(exception.getMessage())
                .contains("Cannot execute guardrails")
                .contains("event loop thread")
                .contains("blocking");
    }

    /**
     * Tests that tools with output guardrails also throw exception on event loop.
     */
    @Test
    void toolWithOutputGuardrail_throwsException_whenCalledFromEventLoop() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> exceptionRef = new AtomicReference<>();

        vertx.runOnContext(v -> {
            try {
                assertThat(io.vertx.core.Context.isOnEventLoopThread()).isTrue();

                Arc.container().requestContext().activate();
                try {
                    aiService.chat("test", "toolWithOutputGuardrail - hello");
                } finally {
                    Arc.container().requestContext().terminate();
                }
            } catch (Throwable e) {
                exceptionRef.set(e);
            } finally {
                latch.countDown();
            }
        });

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

        Throwable exception = exceptionRef.get();
        assertThat(exception)
                .isNotNull()
                .isInstanceOf(BlockingToolNotAllowedException.class);

        assertThat(exception.getMessage())
                .contains("Cannot execute guardrails")
                .contains("event loop thread")
                .contains("blocking");
    }

    /**
     * Verifies that guardrails work correctly when NOT on event loop.
     * <p>
     * This test runs on the regular test thread (not event loop).
     * </p>
     */
    @Test
    void toolWithGuardrails_worksCorrectly_whenNotOnEventLoop() {
        // Verify we're NOT on event loop
        assertThat(io.vertx.core.Context.isOnEventLoopThread())
                .as("Test setup: should NOT be on event loop")
                .isFalse();

        // Activate request context manually since we're not using @ActivateRequestContext
        Arc.container().requestContext().activate();
        try {
            String result = aiService.chat("test", "toolWithInputGuardrail - hello");

            // Verify guardrail was executed
            assertThat(SimpleInputGuardrail.executed).isTrue();
            assertThat(SimpleInputGuardrail.ranOnEventLoop).isFalse();

            // Verify tool executed successfully
            assertThat(MyTools.toolWithInputGuardrailExecuted).isTrue();
            assertThat(result).contains("Input: hello");
        } finally {
            Arc.container().requestContext().terminate();
        }
    }

    /**
     * Verifies that output guardrails work correctly when NOT on event loop.
     */
    @Test
    void toolWithOutputGuardrail_worksCorrectly_whenNotOnEventLoop() {
        assertThat(io.vertx.core.Context.isOnEventLoopThread()).isFalse();

        Arc.container().requestContext().activate();
        try {
            String result = aiService.chat("test", "toolWithOutputGuardrail - secret");

            // Verify guardrail was executed
            assertThat(SimpleOutputGuardrail.executed).isTrue();
            assertThat(SimpleOutputGuardrail.ranOnEventLoop).isFalse();

            // Verify tool executed successfully
            assertThat(MyTools.toolWithOutputGuardrailExecuted).isTrue();

            // Verify output was filtered by guardrail
            assertThat(result).contains("[FILTERED]");
            assertThat(result).doesNotContain("secret");
        } finally {
            Arc.container().requestContext().terminate();
        }
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface MyAiService {
        @ToolBox(MyTools.class)
        String chat(@MemoryId String memoryId, @UserMessage String userMessage);
    }

    @ApplicationScoped
    public static class MyTools {
        static boolean toolWithInputGuardrailExecuted = false;
        static boolean toolWithOutputGuardrailExecuted = false;

        @Tool("Tool with input guardrail")
        @ToolInputGuardrails({ SimpleInputGuardrail.class })
        public String toolWithInputGuardrail(String input) {
            toolWithInputGuardrailExecuted = true;
            return "Input: " + input;
        }

        @Tool("Tool with output guardrail")
        @ToolOutputGuardrails({ SimpleOutputGuardrail.class })
        public String toolWithOutputGuardrail(String input) {
            toolWithOutputGuardrailExecuted = true;
            return "Output: " + input;
        }

        static void reset() {
            toolWithInputGuardrailExecuted = false;
            toolWithOutputGuardrailExecuted = false;
        }
    }

    /**
     * Simple input guardrail that tracks execution context.
     */
    @ApplicationScoped
    public static class SimpleInputGuardrail implements ToolInputGuardrail {
        static boolean executed = false;
        static boolean ranOnEventLoop = false;

        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            executed = true;
            ranOnEventLoop = io.vertx.core.Context.isOnEventLoopThread();

            JsonObject args = request.argumentsAsJson();
            String input = args.getString("input");

            if (input == null || input.isEmpty()) {
                return ToolInputGuardrailResult.failure("Input is required");
            }

            return ToolInputGuardrailResult.success();
        }

        static void reset() {
            executed = false;
            ranOnEventLoop = false;
        }
    }

    /**
     * Simple output guardrail that filters sensitive data.
     */
    @ApplicationScoped
    public static class SimpleOutputGuardrail implements ToolOutputGuardrail {
        static boolean executed = false;
        static boolean ranOnEventLoop = false;

        @Override
        public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
            executed = true;
            ranOnEventLoop = io.vertx.core.Context.isOnEventLoopThread();

            String result = request.resultText();
            String filtered = result.replace("secret", "[FILTERED]");

            if (!filtered.equals(result)) {
                return ToolOutputGuardrailResult.successWith(
                        dev.langchain4j.service.tool.ToolExecutionResult.builder()
                                .resultText(filtered)
                                .build());
            }

            return ToolOutputGuardrailResult.success();
        }

        static void reset() {
            executed = false;
            ranOnEventLoop = false;
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
