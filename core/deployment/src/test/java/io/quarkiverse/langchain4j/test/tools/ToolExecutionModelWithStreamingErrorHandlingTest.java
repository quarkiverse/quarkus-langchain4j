package io.quarkiverse.langchain4j.test.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.shaded.org.awaitility.Awaitility;

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
import io.quarkiverse.langchain4j.runtime.BlockingToolNotAllowedException;
import io.quarkiverse.langchain4j.test.Lists;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;
import io.vertx.core.Vertx;

/**
 * Test that tool execution errors are properly propagated to the stream's error handler
 * when the streaming response callback arrives on the event loop and tool execution
 * is dispatched to a worker thread.
 *
 * This test reproduces a bug where exceptions thrown during tool execution on a worker thread
 * (dispatched via executeBlocking) are swallowed because the Future is not awaited,
 * causing the stream to hang indefinitely.
 */
public class ToolExecutionModelWithStreamingErrorHandlingTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyAiService.class, Lists.class));

    @Inject
    MyAiService aiService;

    @Inject
    Vertx vertx;

    /**
     * Test that tool execution errors are properly propagated when:
     * 1. Streaming callback arrives on event loop (simulating real HTTP client behavior)
     * 2. Tool requires worker thread dispatch (blocking tool)
     * 3. Tool throws an exception
     *
     * Without the fix, this test will timeout because the exception is swallowed
     * by executeBlocking and the stream hangs.
     */
    @Test
    @ActivateRequestContext
    void testBlockingToolExceptionIsProperlyPropagated() {
        String uuid = UUID.randomUUID().toString();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<String> result = new AtomicReference<>();

        // Subscribe from a worker thread (simulating @RunOnVirtualThread)
        // The mock will deliver callbacks on the event loop regardless
        aiService.helloFailingBlocking("abc", "hiFailingBlocking - " + uuid)
                .collect().asList().map(l -> String.join(" ", l))
                .subscribeAsCompletionStage()
                .whenComplete((r, t) -> {
                    if (t != null) {
                        failure.set(t);
                    } else {
                        result.set(r);
                    }
                });

        // Without the fix, this will timeout because the stream hangs
        Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).until(() -> failure.get() != null || result.get() != null);

        assertThat(result.get()).isNotNull();
        assertThat(result.get()).contains("Tool execution failed intentionally");

        assertThat(failure.get()).isNull();
    }

    @Test
    @ActivateRequestContext
    void testUnexpectedExceptionIsProperlyPropagated() {
        String uuid = UUID.randomUUID().toString();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<String> result = new AtomicReference<>();

        // Subscribe from a worker thread (simulating @RunOnVirtualThread)
        // The mock will deliver callbacks on the event loop regardless
        aiService.helloNotAllowedBlocking("abc", "hiNotAllowedBlocking - " + uuid)
                .collect().asList().map(l -> String.join(" ", l))
                .subscribeAsCompletionStage()
                .whenComplete((r, t) -> {
                    if (t != null) {
                        failure.set(t);
                    } else {
                        result.set(r);
                    }
                });

        // Without the fix, this will timeout because the stream hangs
        Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).until(() -> failure.get() != null || result.get() != null);

        assertThat(result.get()).isNull();
        assertThat(failure.get()).isNotNull();
        assertThat(failure.get()).hasMessageContaining("Cannot execute blocking tools on event loop thread");
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = EventLoopCallbackChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface MyAiService {

        @ToolBox(FailingBlockingTool.class)
        Multi<String> helloFailingBlocking(@MemoryId String memoryId, @UserMessage String userMessageContainingTheToolId);

        @ToolBox(BlockingNotAllowedTool.class)
        Multi<String> helloNotAllowedBlocking(@MemoryId String memoryId, @UserMessage String userMessageContainingTheToolId);
    }

    @Singleton
    public static class FailingBlockingTool {
        // This tool is blocking (no @NonBlocking) and returns List (imperative)
        // This forces worker thread dispatch when called from event loop
        @Tool
        public List<String> hiFailingBlocking(String m) {
            throw new RuntimeException("Tool execution failed intentionally");
        }
    }

    @Singleton
    public static class BlockingNotAllowedTool {
        @Tool
        public List<String> hiNotAllowedBlocking(String m) {
            throw new BlockingToolNotAllowedException("Cannot execute blocking tools on event loop thread");
        }
    }

    public static class EventLoopCallbackChatModelSupplier implements Supplier<StreamingChatModel> {

        @Override
        public StreamingChatModel get() {
            // Get Vertx from CDI programmatically since Supplier is not a CDI bean
            Vertx vertx = Arc.container().instance(Vertx.class).get();
            return new EventLoopCallbackChatModel(vertx);
        }
    }

    /**
     * Mock chat model that simulates real HTTP client behavior by delivering callbacks on the event loop.
     * Real streaming HTTP clients (like those used by OpenAI, Anthropic, etc.) deliver responses
     * via Vert.x HTTP client callbacks which run on the event loop.
     */
    public static class EventLoopCallbackChatModel implements StreamingChatModel {

        private final Vertx vertx;

        public EventLoopCallbackChatModel(Vertx vertx) {
            this.vertx = vertx;
        }

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            List<ChatMessage> messages = chatRequest.messages();
            // Simulate async HTTP client - deliver response on event loop
            // This is the key difference from the synchronous mock - it simulates real HTTP client behavior
            vertx.getOrCreateContext().runOnContext(v -> {
                if (messages.size() == 1) {
                    String text = ((dev.langchain4j.data.message.UserMessage) messages.get(0)).singleText();
                    var segments = text.split(" - ");
                    var toolId = segments[0];
                    var content = segments[1];
                    ChatResponse chatResponse = ChatResponse.builder()
                            .aiMessage(new AiMessage("cannot be blank", List.of(ToolExecutionRequest.builder()
                                    .id("my-tool-" + toolId)
                                    .name(toolId)
                                    .arguments("{\"m\":\"" + content + "\"}")
                                    .build())))
                            .tokenUsage(new TokenUsage(0, 0)).finishReason(FinishReason.TOOL_EXECUTION).build();
                    handler.onCompleteResponse(chatResponse);
                } else if (messages.size() == 3) {
                    ToolExecutionResultMessage last = (ToolExecutionResultMessage) Lists.last(messages);
                    handler.onPartialResponse("response: ");
                    handler.onPartialResponse(last.text());
                    handler.onCompleteResponse(
                            ChatResponse.builder().aiMessage(new AiMessage("")).tokenUsage(new TokenUsage(0, 0))
                                    .finishReason(FinishReason.STOP).build());
                } else {
                    handler.onError(new RuntimeException("Invalid number of messages: " + messages.size()));
                }
            });
        }
    }

    public static class MyMemoryProviderSupplier implements Supplier<ChatMemoryProvider> {
        @Override
        public ChatMemoryProvider get() {
            return new ChatMemoryProvider() {
                @Override
                public ChatMemory get(Object memoryId) {
                    return MessageWindowChatMemory.withMaxMessages(10);
                }
            };
        }
    }
}
