package io.quarkiverse.langchain4j.test.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatEvent;
import io.quarkiverse.langchain4j.test.Lists;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.Cancellable;

/**
 * Test that cancelling a streaming Multi subscription stops the agent tool execution loop
 * and leaves memory in a consistent state.
 * <p>
 * This verifies that after cancellation:
 * <ul>
 * <li>Tools are NOT called for subsequent rounds</li>
 * <li>No additional chat() calls are made to the model</li>
 * <li>The stream terminates and doesn't hang</li>
 * <li>Memory is consistent: every tool request has a matching tool result</li>
 * </ul>
 */
public class ToolExecutionModelWithStreamingCancellationTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyAiService.class, Lists.class));

    @Inject
    MyAiService aiService;

    /**
     * Test that cancelling the Multi subscription prevents subsequent tool rounds
     * and leaves memory consistent.
     * <p>
     * The mock model responds with two tool requests per round. The first tool blocks
     * while the test cancels the subscription. After cancellation, the second tool should
     * receive a cancellation result instead of executing, keeping memory consistent.
     * <p>
     * Flow:
     * 1. Model responds with 2 tool requests (on a new thread)
     * 2. First tool starts executing, signals TOOL_STARTED, then blocks on TOOL_MAY_PROCEED
     * 3. Test detects TOOL_STARTED, cancels the subscription (sets cancelled flag)
     * 4. Test signals TOOL_MAY_PROCEED, first tool completes
     * 5. Handler sees isCancelled() for second tool — fills cancellation result
     * 6. Handler checks isCancelled() after tool loop — sees true, stops
     */
    @Test
    @ActivateRequestContext
    void testCancellationStopsToolLoop() throws InterruptedException {
        // Reset shared state
        CountableTool.CALL_COUNT.set(0);
        CountableTool.TOOL_STARTED = new CountDownLatch(1);
        CountableTool.TOOL_MAY_PROCEED = new CountDownLatch(1);
        AsyncMultiRoundChatModel.CHAT_CALL_COUNT.set(0);
        MyMemoryProviderSupplier.MEMORIES.clear();

        List<ChatEvent> receivedEvents = new CopyOnWriteArrayList<>();

        // Subscribe to the Multi<ChatEvent> stream
        Cancellable cancellable = aiService.chat("mem1", "trigger")
                .onItem().invoke(receivedEvents::add)
                .subscribe().with(
                        item -> {
                        },
                        failure -> {
                        });

        // Wait for the first tool to start executing (on the model's thread)
        assertThat(CountableTool.TOOL_STARTED.await(5, TimeUnit.SECONDS))
                .as("First tool execution should start within timeout")
                .isTrue();

        // Cancel the subscription — this sets the AtomicBoolean cancelled flag
        cancellable.cancel();

        // Now let the tool finish — the handler will check isCancelled() for the next tool
        CountableTool.TOOL_MAY_PROCEED.countDown();

        // Give time for any in-flight operations to settle
        Thread.sleep(500);

        // Assert: tool was called exactly once (first tool only, second was cancelled)
        assertThat(CountableTool.CALL_COUNT.get())
                .as("Tool should only be called once (first tool), not for the second tool after cancellation")
                .isEqualTo(1);

        // Assert: model chat() was called at most 2 times (initial + possibly one before cancellation took effect)
        // Without cancellation, it would be called 4 times (initial + 3 tool rounds)
        assertThat(AsyncMultiRoundChatModel.CHAT_CALL_COUNT.get())
                .as("Model should not make additional chat() calls after cancellation")
                .isLessThanOrEqualTo(2);

        // Assert: memory is in a consistent state after cancellation
        // Every AiMessage with tool requests must have matching ToolExecutionResultMessages
        ChatMemory memory = MyMemoryProviderSupplier.MEMORIES.get("mem1");
        assertThat(memory).as("Memory should exist for memoryId 'mem1'").isNotNull();

        List<ChatMessage> messages = memory.messages();
        // The last message should be a ToolExecutionResultMessage, not an orphaned AiMessage
        ChatMessage lastMessage = messages.get(messages.size() - 1);
        assertThat(lastMessage)
                .as("Last message in memory should be a ToolExecutionResultMessage, not an orphaned AiMessage")
                .isInstanceOf(ToolExecutionResultMessage.class);

        // Count tool requests vs tool results — they must match
        long toolRequestCount = messages.stream()
                .filter(m -> m instanceof AiMessage)
                .map(m -> (AiMessage) m)
                .filter(AiMessage::hasToolExecutionRequests)
                .mapToLong(m -> m.toolExecutionRequests().size())
                .sum();
        long toolResultCount = messages.stream()
                .filter(m -> m instanceof ToolExecutionResultMessage)
                .count();
        assertThat(toolResultCount)
                .as("Number of tool results must match number of tool requests for memory consistency")
                .isEqualTo(toolRequestCount);

        // Verify that cancelled tool results contain the cancellation message
        boolean hasCancelledResult = messages.stream()
                .filter(m -> m instanceof ToolExecutionResultMessage)
                .map(m -> (ToolExecutionResultMessage) m)
                .anyMatch(m -> m.text().equals("Tool execution was cancelled"));
        assertThat(hasCancelledResult)
                .as("Memory should contain at least one cancelled tool execution result")
                .isTrue();
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface MyAiService {
        @ToolBox(CountableTool.class)
        Multi<ChatEvent> chat(@MemoryId String memoryId, @UserMessage String message);
    }

    @Singleton
    public static class CountableTool {
        static final AtomicInteger CALL_COUNT = new AtomicInteger(0);
        static volatile CountDownLatch TOOL_STARTED = new CountDownLatch(1);
        static volatile CountDownLatch TOOL_MAY_PROCEED = new CountDownLatch(1);

        @Tool
        public String doWork(String input) {
            int call = CALL_COUNT.incrementAndGet();
            if (call == 1) {
                // First invocation: signal the test, then wait for permission to proceed
                TOOL_STARTED.countDown();
                try {
                    TOOL_MAY_PROCEED.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return "result-" + input;
        }
    }

    public static class MyChatModelSupplier implements Supplier<StreamingChatModel> {
        @Override
        public StreamingChatModel get() {
            return new AsyncMultiRoundChatModel();
        }
    }

    /**
     * A mock streaming chat model that responds asynchronously on a new thread.
     * This simulates real model behavior where responses arrive on I/O threads
     * and ensures tool execution happens on a separate thread from the test.
     * <p>
     * The model requests two tool executions per round (to test mid-batch cancellation)
     * for up to 3 rounds, then returns a final response.
     */
    public static class AsyncMultiRoundChatModel implements StreamingChatModel {
        static final AtomicInteger CHAT_CALL_COUNT = new AtomicInteger(0);

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            CHAT_CALL_COUNT.incrementAndGet();
            List<ChatMessage> messages = chatRequest.messages();

            // Count tool result messages to determine the round (2 results per round)
            long toolResultCount = messages.stream()
                    .filter(m -> m instanceof ToolExecutionResultMessage)
                    .count();
            long round = toolResultCount / 2;

            // Respond asynchronously on a new thread to simulate real I/O behavior
            new Thread(() -> {
                if (round < 3) {
                    // Request two tool executions per round to test mid-batch cancellation:
                    // the first tool blocks while the test cancels, then the second tool
                    // should get a cancellation result instead of executing
                    ChatResponse chatResponse = ChatResponse.builder()
                            .aiMessage(new AiMessage("thinking...", List.of(
                                    ToolExecutionRequest.builder()
                                            .id("tool-call-" + round + "a")
                                            .name("doWork")
                                            .arguments("{\"input\":\"round-" + (round + 1) + "a\"}")
                                            .build(),
                                    ToolExecutionRequest.builder()
                                            .id("tool-call-" + round + "b")
                                            .name("doWork")
                                            .arguments("{\"input\":\"round-" + (round + 1) + "b\"}")
                                            .build())))
                            .tokenUsage(new TokenUsage(0, 0))
                            .finishReason(FinishReason.TOOL_EXECUTION)
                            .build();
                    handler.onCompleteResponse(chatResponse);
                } else {
                    // Final response after all tool rounds
                    handler.onPartialResponse("done");
                    handler.onCompleteResponse(ChatResponse.builder()
                            .aiMessage(new AiMessage("done"))
                            .tokenUsage(new TokenUsage(0, 0))
                            .finishReason(FinishReason.STOP)
                            .build());
                }
            }, "mock-model-thread").start();
        }
    }

    public static class MyMemoryProviderSupplier implements Supplier<ChatMemoryProvider> {
        static final Map<Object, ChatMemory> MEMORIES = new ConcurrentHashMap<>();

        @Override
        public ChatMemoryProvider get() {
            return new ChatMemoryProvider() {
                @Override
                public ChatMemory get(Object memoryId) {
                    return MEMORIES.computeIfAbsent(memoryId,
                            k -> MessageWindowChatMemory.withMaxMessages(20));
                }
            };
        }
    }
}
