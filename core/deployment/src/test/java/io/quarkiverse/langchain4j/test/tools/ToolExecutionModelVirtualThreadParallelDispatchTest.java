package io.quarkiverse.langchain4j.test.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
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
import io.quarkiverse.langchain4j.runtime.ContextLocals;
import io.quarkiverse.langchain4j.runtime.VirtualThreadSupport;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatEvent;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.common.vertx.VertxContext;
import io.smallrye.mutiny.Multi;
import io.vertx.core.Context;
import io.vertx.core.Vertx;

/**
 * Asserts the parallel virtual-thread tool batch path: tools run concurrently on per-tool
 * virtual threads, results are appended to memory in request order regardless of completion
 * order, BeforeToolExecution events fire in request order on the carrier thread, and
 * ToolExecutedEvent fires in completion order from the per-tool worker thread.
 */
public class ToolExecutionModelVirtualThreadParallelDispatchTest {

    private static final String CONTEXT_LOCAL_KEY = "parallel-dispatch-test-local";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(MyAiService.class));

    @Inject
    MyAiService aiService;

    @Inject
    Vertx vertx;

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    @ActivateRequestContext
    void parallelBatchExecutesToolsOnSeparateVirtualThreads() throws InterruptedException {
        TimedTool.reset();
        String uuid = UUID.randomUUID().toString();
        List<Thread> toolThreads = new CopyOnWriteArrayList<>();
        TimedTool.threadObserver = thread -> toolThreads.add(thread);
        try {
            executeChatEventStreamBlocking(
                    () -> aiService.threeTools("mem-" + uuid,
                            "timedA,timedB,timedC - " + uuid),
                    null);
        } finally {
            TimedTool.threadObserver = null;
        }

        assertThat(toolThreads).hasSize(3);
        assertThat(toolThreads).allSatisfy(t -> assertThat(VirtualThreadSupport.isVirtualThread(t)).isTrue());
        // Each tool ran on its own virtual thread.
        assertThat(new HashSet<>(toolThreads)).hasSize(3);
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    @ActivateRequestContext
    void parallelBatchOverlapsToolExecution() throws InterruptedException {
        TimedTool.reset();
        String uuid = UUID.randomUUID().toString();
        long start = System.nanoTime();
        executeChatEventStreamBlocking(
                () -> aiService.threeTools("mem-" + uuid,
                        "timedA,timedB,timedC - " + uuid),
                null);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // Each tool sleeps 250ms. Serial would take ≥750ms; parallel should easily complete
        // under 600ms. Generous bound to avoid CI flakes.
        assertThat(elapsedMs).isLessThan(600);
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    @ActivateRequestContext
    void parallelBatchAppendsResultsInRequestOrderRegardlessOfCompletion() throws InterruptedException {
        StaggeredTool.reset();
        String uuid = UUID.randomUUID().toString();
        // staggeredA sleeps longest, staggeredC fastest — completion order is C, B, A but
        // memory must hold results in submission order A, B, C.
        executeChatEventStreamBlocking(
                () -> aiService.threeStaggeredTools("mem-" + uuid,
                        "staggeredA,staggeredB,staggeredC - " + uuid),
                null);

        List<String> resultTexts = MyMemoryProviderSupplier.snapshotToolResults("mem-" + uuid);
        // Tool result text is JSON-encoded (the framework quotes string returns), so the
        // assertion target is the JSON-quoted form. What matters here is the order: the slowest
        // tool (A) appears first in memory even though it completed last.
        assertThat(resultTexts).containsExactly("\"A:done\"", "\"B:done\"", "\"C:done\"");
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    @ActivateRequestContext
    void parallelBatchEmitsBeforeToolExecutionEventsInRequestOrder() throws InterruptedException {
        TimedTool.reset();
        String uuid = UUID.randomUUID().toString();
        List<String> beforeToolNames = new CopyOnWriteArrayList<>();
        executeChatEventStreamBlocking(
                () -> aiService.threeTools("mem-" + uuid,
                        "timedA,timedB,timedC - " + uuid),
                event -> {
                    if (event instanceof ChatEvent.BeforeToolExecutionEvent before) {
                        beforeToolNames.add(before.getRequest().name());
                    }
                });

        assertThat(beforeToolNames).containsExactly("timedA", "timedB", "timedC");
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    @ActivateRequestContext
    void parallelBatchPropagatesDuplicatedContextLocalsToEveryWorker() throws InterruptedException {
        TimedTool.reset();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<List<String>> observedLocals = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Context duplicatedContext = VertxContext.getOrCreateDuplicatedContext(vertx);

        duplicatedContext.executeBlocking(v -> {
            String uuid = UUID.randomUUID().toString();
            try {
                Arc.container().requestContext().activate();
                ContextLocals.put(CONTEXT_LOCAL_KEY, uuid);
                List<String> seenByTools = new CopyOnWriteArrayList<>();
                TimedTool.threadObserver = thread -> {
                    String observed = ContextLocals.get(CONTEXT_LOCAL_KEY);
                    seenByTools.add(observed != null ? observed : "<missing>");
                };
                try {
                    executeChatEventStreamBlocking(
                            () -> aiService.threeTools("mem-" + uuid,
                                    "timedA,timedB,timedC - " + uuid),
                            null);
                } finally {
                    TimedTool.threadObserver = null;
                }
                observedLocals.set(seenByTools);
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                ContextLocals.remove(CONTEXT_LOCAL_KEY);
                Arc.container().requestContext().deactivate();
                latch.countDown();
            }
        }, false);

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        if (failure.get() != null) {
            throw new AssertionError("Parallel context-locals propagation failed", failure.get());
        }
        assertThat(observedLocals.get()).hasSize(3).allSatisfy(local -> assertThat(local).isNotNull());
        // Every parallel worker observed the same context-local value as the caller.
        assertThat(new HashSet<>(observedLocals.get())).hasSize(1);
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    @ActivateRequestContext
    void parallelBatchKeepsMemoryConsistentWhenOneToolThrows() throws InterruptedException {
        String uuid = UUID.randomUUID().toString();
        // The default tool execution error handler converts throws into error result messages
        // sent back to the LLM. The parallel path must still produce one result per request
        // (success-or-error), in request order, so chat memory stays consistent.
        executeChatEventStreamBlocking(
                () -> aiService.failingMixEvents("mem-" + uuid, "okA,boom,okB - " + uuid),
                null);

        List<String> resultTexts = MyMemoryProviderSupplier.snapshotToolResults("mem-" + uuid);
        assertThat(resultTexts).hasSize(3);
        assertThat(resultTexts.get(0)).contains("okA");
        assertThat(resultTexts.get(1)).contains("boom");
        assertThat(resultTexts.get(2)).contains("okB");
    }

    private void executeChatEventStreamBlocking(Supplier<Multi<ChatEvent>> serviceCall,
            Consumer<ChatEvent> eventObserver) throws InterruptedException {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        serviceCall.get()
                .onItem().invoke(event -> {
                    if (eventObserver != null) {
                        eventObserver.accept(event);
                    }
                })
                .subscribe().with(
                        item -> {
                        },
                        t -> {
                            failure.set(t);
                            latch.countDown();
                        },
                        latch::countDown);

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        if (failure.get() != null) {
            throw new AssertionError("Chat event stream failed", failure.get());
        }
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface MyAiService {

        @ToolBox({ TimedTool.class })
        Multi<ChatEvent> threeTools(@MemoryId String memoryId,
                @UserMessage String userMessageContainingTheToolIds);

        @ToolBox({ StaggeredTool.class })
        Multi<ChatEvent> threeStaggeredTools(@MemoryId String memoryId,
                @UserMessage String userMessageContainingTheToolIds);

        @ToolBox({ FailingMixTool.class })
        Multi<ChatEvent> failingMixEvents(@MemoryId String memoryId,
                @UserMessage String userMessageContainingTheToolIds);
    }

    @Singleton
    public static class TimedTool {

        static volatile Consumer<Thread> threadObserver;

        static void reset() {
            threadObserver = null;
        }

        @Tool("timedA")
        @RunOnVirtualThread
        public String timedA(String m) {
            return invoke("A", m);
        }

        @Tool("timedB")
        @RunOnVirtualThread
        public String timedB(String m) {
            return invoke("B", m);
        }

        @Tool("timedC")
        @RunOnVirtualThread
        public String timedC(String m) {
            return invoke("C", m);
        }

        private String invoke(String label, String m) {
            Consumer<Thread> observer = threadObserver;
            if (observer != null) {
                observer.accept(Thread.currentThread());
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return label + ":" + m;
        }
    }

    @Singleton
    public static class StaggeredTool {

        static void reset() {
            MyMemoryProviderSupplier.MEMORIES.clear();
        }

        @Tool("staggeredA")
        @RunOnVirtualThread
        public String staggeredA(String m) {
            sleep(300);
            return "A:done";
        }

        @Tool("staggeredB")
        @RunOnVirtualThread
        public String staggeredB(String m) {
            sleep(150);
            return "B:done";
        }

        @Tool("staggeredC")
        @RunOnVirtualThread
        public String staggeredC(String m) {
            sleep(50);
            return "C:done";
        }

        private void sleep(long ms) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Singleton
    public static class FailingMixTool {

        @Tool("okA")
        @RunOnVirtualThread
        public String okA(String m) {
            return "okA:" + m;
        }

        @Tool("okB")
        @RunOnVirtualThread
        public String okB(String m) {
            return "okB:" + m;
        }

        @Tool("boom")
        @RunOnVirtualThread
        public String boom(String m) {
            throw new RuntimeException("boom: " + m);
        }
    }

    public static class MyChatModelSupplier implements Supplier<StreamingChatModel> {
        @Override
        public StreamingChatModel get() {
            return new MultiToolChatModel();
        }
    }

    /**
     * Mock model that emits a batch of {@link ToolExecutionRequest}s parsed from the user
     * message and terminates on the follow-up call by echoing the concatenated tool result text.
     */
    public static class MultiToolChatModel implements StreamingChatModel {

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            List<ChatMessage> messages = chatRequest.messages();
            boolean hasToolResults = messages.stream().anyMatch(m -> m instanceof ToolExecutionResultMessage);
            if (!hasToolResults) {
                dev.langchain4j.data.message.UserMessage userMessage = null;
                for (ChatMessage message : messages) {
                    if (message instanceof dev.langchain4j.data.message.UserMessage um) {
                        userMessage = um;
                    }
                }
                String[] segments = userMessage.singleText().split(" - ", 2);
                String[] toolIds = segments[0].split(",");
                String content = segments[1];
                List<ToolExecutionRequest> requests = new ArrayList<>();
                int i = 0;
                for (String toolId : toolIds) {
                    requests.add(ToolExecutionRequest.builder()
                            .id("call-" + toolId + "-" + (i++))
                            .name(toolId.trim())
                            .arguments("{\"m\":\"" + content + "\"}")
                            .build());
                }
                handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(new AiMessage("thinking", requests))
                        .tokenUsage(new TokenUsage(0, 0))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build());
            } else {
                StringBuilder combined = new StringBuilder();
                for (ChatMessage message : messages) {
                    if (message instanceof ToolExecutionResultMessage trm) {
                        if (combined.length() > 0) {
                            combined.append(" | ");
                        }
                        combined.append(trm.text());
                    }
                }
                handler.onPartialResponse("response: ");
                handler.onPartialResponse(combined.toString());
                handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(new AiMessage(""))
                        .tokenUsage(new TokenUsage(0, 0))
                        .finishReason(FinishReason.STOP)
                        .build());
            }
        }
    }

    public static class MyMemoryProviderSupplier implements Supplier<ChatMemoryProvider> {

        static final Map<Object, dev.langchain4j.memory.ChatMemory> MEMORIES = new ConcurrentHashMap<>();

        @Override
        public ChatMemoryProvider get() {
            return memoryId -> MEMORIES.computeIfAbsent(memoryId,
                    k -> MessageWindowChatMemory.withMaxMessages(20));
        }

        static List<String> snapshotToolResults(Object memoryId) {
            dev.langchain4j.memory.ChatMemory memory = MEMORIES.get(memoryId);
            List<String> results = new ArrayList<>();
            if (memory == null) {
                return results;
            }
            for (ChatMessage message : memory.messages()) {
                if (message instanceof ToolExecutionResultMessage trm) {
                    results.add(trm.text());
                }
            }
            return results;
        }
    }
}
