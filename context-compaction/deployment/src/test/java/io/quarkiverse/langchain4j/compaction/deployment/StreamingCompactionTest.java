package io.quarkiverse.langchain4j.compaction.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

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
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.compaction.Summarizer;
import io.quarkus.test.QuarkusExtensionTest;
import io.smallrye.mutiny.Multi;
import io.vertx.core.Context;
import io.vertx.core.Vertx;

/**
 * Streaming AI services reach the chat memory from Vert.x event loop threads: the caller of a {@code Multi} endpoint,
 * and the callbacks of streaming model clients. A summarizing strategy calls the model while the memory is read, so
 * this test records the thread of every summarization.
 */
class StreamingCompactionTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .withApplicationRoot(jar -> jar.addClasses(Assistant.class, Agent.class, FakeStreamingModel.class,
                    FakeStreamingModelSupplier.class, RecordingSummarizer.class, Lookup.class, CompactionThreads.class,
                    NonBlockingAgent.class, NonBlockingLookup.class,
                    CompactionProfilesTest.FakeModel.class, CompactionProfilesTest.FakeModelSupplier.class))
            .overrideConfigKey("quarkus.langchain4j.compaction.strategy", "rolling-summary")
            .overrideConfigKey("quarkus.langchain4j.compaction.summary.keep-last-turns", "1")
            .overrideConfigKey("quarkus.langchain4j.compaction.budget.context-window", "600")
            .overrideConfigKey("quarkus.langchain4j.compaction.budget.reserved-completion-tokens", "0");

    @RegisterAiService(chatLanguageModelSupplier = CompactionProfilesTest.FakeModelSupplier.class, streamingChatLanguageModelSupplier = FakeStreamingModelSupplier.class)
    @ApplicationScoped
    interface Assistant {
        Multi<String> chat(@MemoryId String id, @UserMessage String question);
    }

    @RegisterAiService(chatLanguageModelSupplier = CompactionProfilesTest.FakeModelSupplier.class, streamingChatLanguageModelSupplier = FakeStreamingModelSupplier.class, tools = Lookup.class)
    @ApplicationScoped
    interface Agent {
        Multi<String> chat(@MemoryId String id, @UserMessage String question);
    }

    @RegisterAiService(chatLanguageModelSupplier = CompactionProfilesTest.FakeModelSupplier.class, streamingChatLanguageModelSupplier = FakeStreamingModelSupplier.class, tools = NonBlockingLookup.class)
    @ApplicationScoped
    interface NonBlockingAgent {
        Multi<String> chat(@MemoryId String id, @UserMessage String question);
    }

    @ApplicationScoped
    public static class NonBlockingLookup {
        @Tool("Looks up an order")
        @io.smallrye.common.annotation.NonBlocking
        public String lookup() {
            return "order shipped";
        }
    }

    @ApplicationScoped
    public static class Lookup {
        @Tool("Looks up an order")
        public String lookup() {
            return "order shipped";
        }
    }

    /**
     * Answers like a real streaming client: on a Vert.x event loop. Asks for the tool once when the service has tools.
     */
    @Singleton
    @io.quarkus.arc.Unremovable
    public static class FakeStreamingModel implements StreamingChatModel {

        @Inject
        Vertx vertx;

        @Override
        public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
            boolean hasTools = request.toolSpecifications() != null && !request.toolSpecifications().isEmpty();
            boolean toolCalled = request.messages().get(request.messages().size() - 1) instanceof ToolExecutionResultMessage;
            Context context = vertx.getOrCreateContext();
            context.runOnContext(v -> {
                if (hasTools && !toolCalled) {
                    handler.onCompleteResponse(ChatResponse.builder().aiMessage(AiMessage.from(ToolExecutionRequest
                            .builder().id("call-1").name("lookup").arguments("{}").build())).build());
                } else {
                    handler.onPartialResponse("Your order ");
                    handler.onPartialResponse("has shipped.");
                    handler.onCompleteResponse(
                            ChatResponse.builder().aiMessage(AiMessage.from("Your order has shipped.")).build());
                }
            });
        }
    }

    public static class FakeStreamingModelSupplier implements Supplier<StreamingChatModel> {
        @Override
        public StreamingChatModel get() {
            return io.quarkus.arc.Arc.container().instance(FakeStreamingModel.class).get();
        }
    }

    /**
     * Replaces the default summarizer (which calls a blocking chat model) and records where it runs.
     */
    @Singleton
    public static class RecordingSummarizer implements Summarizer {
        final List<String> eventLoopCalls = new CopyOnWriteArrayList<>();
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public String summarize(String previousSummary, List<ChatMessage> messages, int maxWords) {
            calls.incrementAndGet();
            System.out.println("SUMMARIZE on " + Thread.currentThread().getName() + " el=" + Context.isOnEventLoopThread());
            new Exception("TRACE").printStackTrace(System.out);
            if (Context.isOnEventLoopThread()) {
                eventLoopCalls.add(Thread.currentThread().getName());
            }
            return "The customer asked about several orders.";
        }

        List<String> eventLoopCalls() {
            return eventLoopCalls;
        }

        int calls() {
            return calls.get();
        }

        void reset() {
            eventLoopCalls.clear();
            calls.set(0);
        }
    }

    /**
     * Compaction reports are fired on the thread that compacted the conversation.
     */
    @ApplicationScoped
    public static class CompactionThreads {
        final List<String> all = new CopyOnWriteArrayList<>();
        final List<String> eventLoop = new CopyOnWriteArrayList<>();

        void onReport(@jakarta.enterprise.event.Observes io.quarkiverse.langchain4j.compaction.CompactionReport report) {
            all.add(Thread.currentThread().getName());
            if (Context.isOnEventLoopThread()) {
                eventLoop.add(Thread.currentThread().getName());
            }
        }

        List<String> all() {
            return all;
        }

        List<String> eventLoop() {
            return eventLoop;
        }

        void reset() {
            all.clear();
            eventLoop.clear();
        }
    }

    @Inject
    CompactionThreads compactions;

    @Inject
    Assistant assistant;

    @Inject
    Agent agent;

    @Inject
    NonBlockingAgent nonBlockingAgent;

    @Inject
    RecordingSummarizer summarizer;

    @Inject
    ChatMemoryStore store;

    @Inject
    Vertx vertx;

    @BeforeEach
    void reset() {
        summarizer.reset();
        compactions.reset();
    }

    /**
     * A conversation long enough to be compacted on the next read.
     */
    private void longConversation(String id) {
        List<ChatMessage> history = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            history.add(dev.langchain4j.data.message.UserMessage.from("Where is order " + i + "? " + "x".repeat(200)));
            history.add(AiMessage.from("Order " + i + " is on its way. " + "y".repeat(200)));
        }
        store.updateMessages(id, history);
    }

    private static String collect(Multi<String> stream) {
        return String.join("", stream.collect().asList().await().atMost(Duration.ofSeconds(10)));
    }

    @Test
    void streamingCallFromAWorkerThread() {
        longConversation("worker");
        assertThat(collect(assistant.chat("worker", "And order 11?"))).isEqualTo("Your order has shipped.");
        assertThat(summarizer.calls()).isPositive();
        assertThat(summarizer.eventLoopCalls()).isEmpty();
        assertThat(compactions.eventLoop()).isEmpty();
    }

    @Test
    void streamingCallFromTheEventLoop() throws Exception {
        longConversation("event-loop");
        // a Multi endpoint of Quarkus REST is invoked on the event loop
        CompletableFuture<String> result = new CompletableFuture<>();
        vertx.getOrCreateContext().runOnContext(v -> assistant.chat("event-loop", "And order 11?")
                .collect().asList().subscribe().with(list -> result.complete(String.join("", list)),
                        result::completeExceptionally));
        assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo("Your order has shipped.");
        assertThat(summarizer.calls()).isPositive();
        assertThat(summarizer.eventLoopCalls()).as("summarizations on the event loop").isEmpty();
        // core runs the invocation on a worker thread
        assertThat(compactions.eventLoop()).as("compactions on the event loop").isEmpty();
    }

    @Test
    void streamingToolLoop() {
        longConversation("tools");
        // the request following the tool call is built in the callback of the streaming client, which runs on the
        // event loop; core switches to another thread to execute blocking tools, and builds the request there
        assertThat(collect(agent.chat("tools", "And order 11?"))).isEqualTo("Your order has shipped.");
        assertThat(compactions.all()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(compactions.eventLoop()).as("compactions on the event loop").isEmpty();
    }

    /**
     * With non-blocking tools only, core would stay on the event loop to execute the tools and to build the next
     * request, compacting the conversation there: the extension declares the chat memory as blocking, so core switches
     * to a worker thread.
     */
    @Test
    void streamingToolLoopWithNonBlockingToolFromTheEventLoop() throws Exception {
        longConversation("non-blocking");
        CompletableFuture<String> result = new CompletableFuture<>();
        vertx.getOrCreateContext().runOnContext(v -> nonBlockingAgent.chat("non-blocking", "And order 11?")
                .collect().asList().subscribe().with(list -> result.complete(String.join("", list)),
                        result::completeExceptionally));
        assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo("Your order has shipped.");
        assertThat(compactions.all()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(compactions.eventLoop()).as("compactions on the event loop").isEmpty();
    }
}
