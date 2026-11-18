package io.quarkiverse.langchain4j.test.toolresolution;

import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteResponse;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialResponse;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
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
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.TokenStream;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusExtensionTest;
import io.smallrye.common.annotation.NonBlocking;
import io.smallrye.mutiny.Multi;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

/**
 * The model responses are delivered on a Vert.x event loop, as they are by the Vert.x based model clients. The tools can
 * run on the event loop, so only the concurrent execution requires leaving it: waiting for the tools there would block
 * it. Each tool checks that the event loop is still able to run a task while the tools are executing, and the tools are
 * awaited on a virtual thread, which then sends the follow-up request, whatever the execution model of the tools.
 */
public class ExecuteToolsConcurrentlyEventLoopTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(NonBlockingTools.class, BlockingTools.class, StreamingModelSupplier.class,
                            EventLoopStreamingModel.class));

    static volatile List<String> toolsToCall;
    static volatile List<ChatMessage> lastMessages;
    static volatile Context deliveryContext;
    static volatile String followUpRequestThread;

    public static class StreamingModelSupplier implements Supplier<StreamingChatModel> {
        @Override
        public StreamingChatModel get() {
            return new EventLoopStreamingModel();
        }
    }

    public static class EventLoopStreamingModel implements StreamingChatModel {

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            List<ChatMessage> messages = chatRequest.messages();
            if (messages.get(messages.size() - 1) instanceof ToolExecutionResultMessage) {
                followUpRequestThread = Thread.currentThread().getName();
            }
            Vertx vertx = Arc.container().instance(Vertx.class).get();
            vertx.runOnContext(new Handler<Void>() {
                @Override
                public void handle(Void ignored) {
                    deliveryContext = Vertx.currentContext();
                    respond(chatRequest, handler);
                }
            });
        }

        private static void respond(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            List<ChatMessage> messages = chatRequest.messages();
            if (messages.get(messages.size() - 1) instanceof ToolExecutionResultMessage) {
                lastMessages = messages;
                onPartialResponse(handler, "done");
                onCompleteResponse(handler, ChatResponse.builder()
                        .aiMessage(AiMessage.from("done"))
                        .tokenUsage(new TokenUsage(1, 1))
                        .finishReason(FinishReason.STOP)
                        .build());
                return;
            }
            List<ToolExecutionRequest> requests = new ArrayList<>();
            for (String name : toolsToCall) {
                requests.add(ToolExecutionRequest.builder().id(name).name(name).arguments("{}").build());
            }
            onCompleteResponse(handler, ChatResponse.builder()
                    .aiMessage(AiMessage.from(requests))
                    .tokenUsage(new TokenUsage(1, 1))
                    .finishReason(FinishReason.TOOL_EXECUTION)
                    .build());
        }
    }

    @ApplicationScoped
    public static class NonBlockingTools {

        static volatile CountDownLatch latch;

        @Tool
        @NonBlocking
        public String first() throws InterruptedException {
            return awaitOtherAndCheckEventLoop("first");
        }

        @Tool
        @NonBlocking
        public String second() throws InterruptedException {
            return awaitOtherAndCheckEventLoop("second");
        }

        static String awaitOtherAndCheckEventLoop(String name) throws InterruptedException {
            latch.countDown();
            boolean concurrent = latch.await(500, TimeUnit.MILLISECONDS);
            CountDownLatch eventLoopTaskRan = new CountDownLatch(1);
            deliveryContext.runOnContext(new Handler<Void>() {
                @Override
                public void handle(Void ignored) {
                    eventLoopTaskRan.countDown();
                }
            });
            boolean eventLoopFree = eventLoopTaskRan.await(500, TimeUnit.MILLISECONDS);
            return name + ":" + (concurrent ? "concurrent" : "sequential") + ":" + (eventLoopFree ? "free" : "blocked");
        }
    }

    @ApplicationScoped
    public static class BlockingTools {

        @Tool
        public String firstBlocking() throws InterruptedException {
            return NonBlockingTools.awaitOtherAndCheckEventLoop("firstBlocking");
        }

        @Tool
        public String secondBlocking() throws InterruptedException {
            return NonBlockingTools.awaitOtherAndCheckEventLoop("secondBlocking");
        }
    }

    @RegisterAiService(executeToolsConcurrently = true, tools = NonBlockingTools.class, streamingChatLanguageModelSupplier = StreamingModelSupplier.class)
    public interface ConcurrentAssistant {
        Multi<String> chat(String message);
    }

    @RegisterAiService(executeToolsConcurrently = true, tools = NonBlockingTools.class, streamingChatLanguageModelSupplier = StreamingModelSupplier.class)
    public interface ConcurrentTokenStreamAssistant {
        TokenStream chat(String message);
    }

    @RegisterAiService(executeToolsConcurrently = true, tools = BlockingTools.class, streamingChatLanguageModelSupplier = StreamingModelSupplier.class)
    public interface ConcurrentBlockingTokenStreamAssistant {
        TokenStream chat(String message);
    }

    @Inject
    ConcurrentAssistant concurrentAssistant;

    @Inject
    ConcurrentBlockingTokenStreamAssistant concurrentBlockingTokenStreamAssistant;

    @Inject
    ConcurrentTokenStreamAssistant concurrentTokenStreamAssistant;

    @BeforeEach
    void reset() {
        NonBlockingTools.latch = new CountDownLatch(2);
        toolsToCall = List.of("first", "second");
        lastMessages = null;
        deliveryContext = null;
        followUpRequestThread = null;
    }

    @Test
    @ActivateRequestContext
    void multiDoesNotWaitForConcurrentToolsOnEventLoop() {
        List<String> chunks = concurrentAssistant.chat("hello").collect().asList().await()
                .atMost(Duration.ofSeconds(10));

        assertThat(String.join("", chunks)).isEqualTo("done");
        assertThat(toolResultTexts()).containsExactly("first:concurrent:free", "second:concurrent:free");
        assertThat(followUpRequestThread).startsWith("quarkus-virtual-thread-");
    }

    @Test
    @ActivateRequestContext
    void tokenStreamDoesNotWaitForConcurrentToolsOnEventLoop() throws Exception {
        assertThat(chat(concurrentTokenStreamAssistant.chat("hello"))).isEqualTo("done");
        assertThat(toolResultTexts()).containsExactly("first:concurrent:free", "second:concurrent:free");
        assertThat(followUpRequestThread).startsWith("quarkus-virtual-thread-");
    }

    @Test
    @ActivateRequestContext
    void tokenStreamWaitsForConcurrentBlockingToolsOnVirtualThread() throws Exception {
        toolsToCall = List.of("firstBlocking", "secondBlocking");

        assertThat(chat(concurrentBlockingTokenStreamAssistant.chat("hello"))).isEqualTo("done");
        assertThat(toolResultTexts()).containsExactly("firstBlocking:concurrent:free", "secondBlocking:concurrent:free");
        assertThat(followUpRequestThread).startsWith("quarkus-virtual-thread-");
    }

    private static String chat(TokenStream tokenStream) throws Exception {
        CompletableFuture<String> outcome = new CompletableFuture<>();
        tokenStream
                .onCompleteResponse(new Consumer<ChatResponse>() {
                    @Override
                    public void accept(ChatResponse response) {
                        outcome.complete(response.aiMessage().text());
                    }
                })
                .onError(new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable error) {
                        outcome.completeExceptionally(error);
                    }
                })
                .start();
        return outcome.get(10, TimeUnit.SECONDS);
    }

    private static List<String> toolResultTexts() {
        List<String> texts = new ArrayList<>();
        for (ChatMessage message : lastMessages) {
            if (message instanceof ToolExecutionResultMessage toolResult) {
                texts.add(toolResult.text());
            }
        }
        return texts;
    }
}
