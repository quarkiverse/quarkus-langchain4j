package io.quarkiverse.langchain4j.test.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
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
import io.quarkiverse.langchain4j.runtime.ContextLocals;
import io.quarkiverse.langchain4j.runtime.VirtualThreadSupport;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatEvent;
import io.quarkiverse.langchain4j.runtime.aiservice.QuarkusAiServiceStreamingResponseHandler;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.virtual.threads.VirtualThreadsRecorder;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.common.vertx.VertxContext;
import io.smallrye.mutiny.Multi;
import io.vertx.core.Context;
import io.vertx.core.Vertx;

public class ToolExecutionModelVirtualThreadDispatchTest {

    private static final String DUPLICATED_CONTEXT_LOCAL_KEY = "dispatch-test-local";
    private static final String MIXED_BATCH_METHOD = "MyAiService#mixedTools";
    private static final String MIXED_BATCH_REQUESTED_TOOLS = "Requested tools: [hi, hiVirtualThread]";
    private static final String MIXED_BATCH_NON_VIRTUAL_TOOLS = "Non-VIRTUAL_THREAD tools: [hi]";
    private static final String MIXED_BATCH_DECISION = "Skipping the full-batch virtual-thread dispatch optimization "
            + "and using legacy per-tool scheduling instead";
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
    void virtualThreadBatchRunsOnVirtualThread() {
        String uuid = UUID.randomUUID().toString();
        String r = aiService.singleVirtualTool("mem-" + uuid, "hiVirtualThread - " + uuid)
                .collect().asList().map(l -> String.join(" ", l)).await().indefinitely();
        assertThat(r).contains(uuid, "quarkus-virtual-thread-");
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    @ActivateRequestContext
    void allVirtualThreadBatchRunsSerializedLoopOnOneVirtualThread() throws InterruptedException {
        String uuid = UUID.randomUUID().toString();
        List<Thread> beforeToolThreads = new java.util.concurrent.CopyOnWriteArrayList<>();
        List<ChatEvent> events = executeChatEventStreamBlocking(
                () -> aiService.virtualToolBatchEvents("mem-" + uuid,
                        "hiVirtualThread,hiVirtualThreadAgain - " + uuid),
                event -> {
                    if (event instanceof ChatEvent.BeforeToolExecutionEvent) {
                        beforeToolThreads.add(Thread.currentThread());
                    }
                });
        String response = extractPartialResponseText(events);

        assertThat(response).contains("VIRTUAL:", "VIRTUAL_AGAIN:", uuid);
        assertThat(beforeToolThreads).hasSize(2);
        assertThat(beforeToolThreads)
                .allSatisfy(thread -> assertThat(VirtualThreadSupport.isVirtualThread(thread)).isTrue());
        assertThat(new HashSet<>(beforeToolThreads)).hasSize(1);
    }

    @Test
    void blockingBatchRunsOnWorkerThread() {
        String uuid = UUID.randomUUID().toString();
        String r = invokeFromEventLoop(() -> aiService.singleBlockingTool("mem-" + uuid, "hi - " + uuid));
        assertThat(r).contains(uuid, "executor-thread");
        assertThat(r).doesNotContain("quarkus-virtual-thread-");
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    void mixedBatchFallsBackToLegacyPerToolScheduling() {
        Logger logger = Logger.getLogger(QuarkusAiServiceStreamingResponseHandler.class.getName());
        CapturingHandler handler = new CapturingHandler();
        logger.addHandler(handler);
        try {
            String uuid = UUID.randomUUID().toString();
            String r = invokeFromEventLoop(() -> aiService.mixedTools("mem-" + uuid, "hi,hiVirtualThread - " + uuid));

            assertThat(r).contains("BLOCKING:").contains("executor-thread");
            assertThat(r).contains("VIRTUAL:").contains("quarkus-virtual-thread-");
            assertThat(r).contains(uuid);
            Awaitility.await().atMost(java.time.Duration.ofSeconds(10))
                    .untilAsserted(() -> assertThat(handler.messages()).anySatisfy(message -> assertThat(message)
                            .contains(MIXED_BATCH_METHOD)
                            .contains(MIXED_BATCH_REQUESTED_TOOLS)
                            .contains(MIXED_BATCH_NON_VIRTUAL_TOOLS)
                            .contains(MIXED_BATCH_DECISION)));
        } finally {
            logger.removeHandler(handler);
        }
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    @ActivateRequestContext
    void virtualThreadToolFailureSurfacesThroughToolErrorHandler() {
        String r = aiService.failingVirtualTool("mem-" + UUID.randomUUID(), "failingVirtualThread - boom")
                .collect().asList().map(l -> String.join(" ", l)).await().indefinitely();
        assertThat(r).contains("boom");
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    void fullVirtualThreadBatchPreservesDuplicatedContextLocalsWhenSubmittedToVirtualThread() throws InterruptedException {
        AtomicReference<String> expectedLocal = new AtomicReference<>();
        AtomicReference<String> observedLocal = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Context duplicatedContext = VertxContext.getOrCreateDuplicatedContext(vertx);

        duplicatedContext.executeBlocking(v -> {
            String uuid = UUID.randomUUID().toString();
            try {
                Arc.container().requestContext().activate();
                expectedLocal.set(uuid);
                ContextLocals.put(DUPLICATED_CONTEXT_LOCAL_KEY, uuid);
                List<ChatEvent> events = executeChatEventStreamBlocking(
                        () -> aiService.virtualToolBatchEvents("mem-" + uuid,
                                "hiVirtualThread,hiVirtualThreadAgain - " + uuid),
                        event -> {
                            if (event instanceof ChatEvent.BeforeToolExecutionEvent) {
                                observedLocal.compareAndSet(null, ContextLocals.get(DUPLICATED_CONTEXT_LOCAL_KEY));
                            }
                        });
                String response = extractPartialResponseText(events);
                assertThat(response).contains(uuid);
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                ContextLocals.remove(DUPLICATED_CONTEXT_LOCAL_KEY);
                Arc.container().requestContext().deactivate();
                latch.countDown();
            }
        }, false);

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        if (failure.get() != null) {
            throw new AssertionError("Duplicated-context submit-path invocation failed", failure.get());
        }
        assertThat(observedLocal.get()).isEqualTo(expectedLocal.get());
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    void fullVirtualThreadBatchPreservesDuplicatedContextLocalsWhenAlreadyOnVirtualThread() throws InterruptedException {
        AtomicReference<String> expectedLocal = new AtomicReference<>();
        AtomicReference<String> observedLocal = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Context duplicatedContext = VertxContext.getOrCreateDuplicatedContext(vertx);

        duplicatedContext.executeBlocking(v -> {
            String uuid = UUID.randomUUID().toString();
            try {
                expectedLocal.set(uuid);
                ContextLocals.put(DUPLICATED_CONTEXT_LOCAL_KEY, uuid);
                VirtualThreadsRecorder.getCurrent().submit(() -> {
                    try {
                        Arc.container().requestContext().activate();
                        List<ChatEvent> events = executeChatEventStreamBlocking(
                                () -> aiService.virtualToolBatchEvents("mem-" + uuid,
                                        "hiVirtualThread,hiVirtualThreadAgain - " + uuid),
                                event -> {
                                    if (event instanceof ChatEvent.BeforeToolExecutionEvent) {
                                        observedLocal.compareAndSet(null, ContextLocals.get(DUPLICATED_CONTEXT_LOCAL_KEY));
                                    }
                                });
                        String response = extractPartialResponseText(events);
                        assertThat(response).contains(uuid);
                        return null;
                    } finally {
                        Arc.container().requestContext().deactivate();
                    }
                }).get();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                ContextLocals.remove(DUPLICATED_CONTEXT_LOCAL_KEY);
                latch.countDown();
            }
        }, false);

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        if (failure.get() != null) {
            throw new AssertionError("Duplicated-context inline-VT invocation failed", failure.get());
        }
        assertThat(observedLocal.get()).isEqualTo(expectedLocal.get());
    }

    private String invokeFromEventLoop(Supplier<Multi<String>> serviceCall) {
        AtomicReference<String> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Context context = vertx.getOrCreateContext();
        context.runOnContext(x -> {
            Arc.container().requestContext().activate();
            try {
                serviceCall.get()
                        .collect().asList().map(l -> String.join(" ", l))
                        .subscribeAsCompletionStage()
                        .whenComplete((r, t) -> {
                            if (t != null) {
                                failure.set(t);
                            } else {
                                result.set(r);
                            }
                            context.runOnContext(ignored -> Arc.container().requestContext().deactivate());
                        });
            } catch (Throwable t) {
                failure.set(t);
                Arc.container().requestContext().deactivate();
            }
        });
        Awaitility.await().atMost(java.time.Duration.ofSeconds(10))
                .until(() -> failure.get() != null || result.get() != null);
        if (failure.get() != null) {
            throw new AssertionError("Service call failed", failure.get());
        }
        return result.get();
    }

    private List<ChatEvent> executeChatEventStreamBlocking(Supplier<Multi<ChatEvent>> serviceCall,
            Consumer<ChatEvent> eventObserver) throws InterruptedException {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        List<ChatEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        serviceCall.get()
                .onItem().invoke(event -> {
                    events.add(event);
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
        return events;
    }

    private String extractPartialResponseText(List<ChatEvent> events) {
        StringBuilder response = new StringBuilder();
        for (ChatEvent event : events) {
            if (event instanceof ChatEvent.PartialResponseEvent partialResponseEvent) {
                response.append(partialResponseEvent.getChunk());
            }
        }
        return response.toString();
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface MyAiService {

        @ToolBox(VirtualTool.class)
        Multi<String> singleVirtualTool(@MemoryId String memoryId, @UserMessage String userMessageContainingTheToolId);

        @ToolBox({ VirtualTool.class, VirtualToolAgain.class })
        Multi<ChatEvent> virtualToolBatchEvents(@MemoryId String memoryId,
                @UserMessage String userMessageContainingTheToolId);

        @ToolBox(BlockingTool.class)
        Multi<String> singleBlockingTool(@MemoryId String memoryId, @UserMessage String userMessageContainingTheToolId);

        @ToolBox({ BlockingTool.class, VirtualTool.class })
        Multi<String> mixedTools(@MemoryId String memoryId, @UserMessage String userMessageContainingTheToolId);

        @ToolBox(FailingVirtualTool.class)
        Multi<String> failingVirtualTool(@MemoryId String memoryId, @UserMessage String userMessageContainingTheToolId);
    }

    @Singleton
    public static class BlockingTool {
        @Tool("hi")
        public String hi(String m) {
            return "BLOCKING:" + m + " " + Thread.currentThread();
        }
    }

    @Singleton
    public static class VirtualTool {
        @Tool("hiVirtualThread")
        @RunOnVirtualThread
        public String hiVirtualThread(String m) {
            return "VIRTUAL:" + m + " " + Thread.currentThread();
        }
    }

    @Singleton
    public static class VirtualToolAgain {
        @Tool("hiVirtualThreadAgain")
        @RunOnVirtualThread
        public String hiVirtualThreadAgain(String m) {
            return "VIRTUAL_AGAIN:" + m + " " + Thread.currentThread();
        }
    }

    @Singleton
    public static class FailingVirtualTool {
        @Tool("failingVirtualThread")
        @RunOnVirtualThread
        public String failingVirtualThread(String m) {
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
     * Chat model that parses a comma-separated list of tool names from the user message
     * (format: {@code "tool1[,tool2] - content"}) and emits one {@link ToolExecutionRequest}
     * per tool in a single {@link AiMessage}. Any follow-up request containing
     * {@link ToolExecutionResultMessage}s terminates the conversation by echoing their
     * concatenated text.
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
                if (userMessage == null) {
                    handler.onError(new RuntimeException("No user message found"));
                    return;
                }
                String text = userMessage.singleText();
                String[] segments = text.split(" - ", 2);
                if (segments.length != 2) {
                    handler.onError(new RuntimeException("Bad user message: " + text));
                    return;
                }
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
                ChatResponse chatResponse = ChatResponse.builder()
                        .aiMessage(new AiMessage("cannot be blank", requests))
                        .tokenUsage(new TokenUsage(0, 0))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build();
                handler.onCompleteResponse(chatResponse);
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

    private static final class CapturingHandler extends Handler {

        private final List<String> messages = new ArrayList<>();

        @Override
        public synchronized void publish(LogRecord record) {
            messages.add(record.getMessage());
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        synchronized List<String> messages() {
            return List.copyOf(messages);
        }
    }
}
