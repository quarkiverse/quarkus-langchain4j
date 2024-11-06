package io.quarkiverse.langchain4j.test.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import jakarta.enterprise.context.RequestScoped;
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
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkiverse.langchain4j.test.Lists;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.virtual.threads.VirtualThreadsRecorder;
import io.smallrye.common.annotation.NonBlocking;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.common.vertx.VertxContext;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;

public class ToolExecutionModelWithStreamingAndRequestScopePropagationTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(MyAiService.class, Lists.class));

    @Inject
    MyAiService aiService;

    @Inject
    Vertx vertx;

    @Inject
    UUIDGenerator uuidGenerator;

    @Test
    @ActivateRequestContext
    void testBlockingToolInvocationFromWorkerThread() {
        String uuid = uuidGenerator.get();
        var r = aiService.hello("abc", "hi")
                .collect().asList().map(l -> String.join(" ", l)).await().indefinitely();
        assertThat(r).contains(uuid, Thread.currentThread().getName()); // We are invoked on the same thread
    }

    @Test
    void testBlockingToolInvocationFromEventLoop() {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<String> result = new AtomicReference<>();
        AtomicReference<String> value = new AtomicReference<>();
        var ctxt = VertxContext.getOrCreateDuplicatedContext(vertx);
        ctxt.runOnContext(x -> {
            try {
                Arc.container().requestContext().activate();
                String uuid = uuidGenerator.get();
                value.set(uuid);
                aiService.hello("abc", "hi")
                        .collect().asList().map(l -> String.join(" ", l))
                        .subscribeAsCompletionStage()
                        .thenAccept(result::set)
                        .whenComplete((r, t) -> Arc.container().requestContext().deactivate());
            } catch (IllegalStateException e) {
                failure.set(e);
                Arc.container().requestContext().deactivate();
            }
        });

        // We would automatically detect this case and switch to a worker thread at subscription time.

        Awaitility.await().until(() -> failure.get() != null || result.get() != null);
        assertThat(failure.get()).isNull();
        assertThat(result.get()).doesNotContain("event", "loop")
                .contains(value.get(), "executor-thread");
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    void testBlockingToolInvocationFromVirtualThread() throws ExecutionException, InterruptedException {
        AtomicReference<String> value = new AtomicReference<>();
        AtomicReference<String> caller = new AtomicReference<>();
        var r = VirtualThreadsRecorder.getCurrent().submit(() -> {
            try {
                Arc.container().requestContext().activate();
                value.set(uuidGenerator.get());
                caller.set(Thread.currentThread().getName());
                return aiService.hello("abc", "hi")
                        .collect().asList().map(l -> String.join(" ", l)).await().indefinitely();
            } finally {
                Arc.container().requestContext().deactivate();
            }
        }).get();

        // The blocking tool is executed on the same thread
        assertThat(r).contains(value.get(), "quarkus-virtual-thread-")
                .contains(caller.get());
    }

    @Test
    @ActivateRequestContext
    void testNonBlockingToolInvocationFromWorkerThread() {
        String uuid = uuidGenerator.get();
        var r = aiService.helloNonBlocking("abc", "hiNonBlocking")
                .collect().asList().map(l -> String.join(" ", l)).await().indefinitely();
        assertThat(r).contains(uuid, Thread.currentThread().getName()); // We are invoked on the same thread
    }

    @Test
    @ActivateRequestContext
    void testNonBlockingToolInvocationFromEventLoop() {
        AtomicReference<String> value = new AtomicReference<>();
        AtomicReference<String> result = new AtomicReference<>();
        AtomicReference<String> caller = new AtomicReference<>();

        var ctxt = VertxContext.getOrCreateDuplicatedContext(vertx);
        ctxt.runOnContext(x -> {
            caller.set(Thread.currentThread().getName());
            Arc.container().requestContext().activate();
            value.set(uuidGenerator.get());
            aiService.helloNonBlocking("abc", "hiNonBlocking")
                    .collect().asList().map(l -> String.join(" ", l))
                    .subscribeAsCompletionStage().thenAccept(result::set)
                    .whenComplete((r, t) -> Arc.container().requestContext().deactivate());
        });

        Awaitility.await().until(() -> result.get() != null);
        assertThat(result.get()).contains(value.get(), caller.get());
    }

    @Test
    @ActivateRequestContext
    void testNonBlockingToolInvocationFromEventLoopWhenWeSwitchToWorkerThread() {
        AtomicReference<String> value = new AtomicReference<>();
        AtomicReference<String> result = new AtomicReference<>();
        AtomicReference<String> caller = new AtomicReference<>();

        var ctxt = VertxContext.getOrCreateDuplicatedContext(vertx);
        ctxt.runOnContext(x -> {
            caller.set(Thread.currentThread().getName());
            Arc.container().requestContext().activate();
            value.set(uuidGenerator.get());
            aiService.helloNonBlockingWithSwitch("abc", "hiNonBlocking")
                    .collect().asList().map(l -> String.join(" ", l))
                    .subscribeAsCompletionStage().thenAccept(result::set)
                    .whenComplete((r, t) -> Arc.container().requestContext().deactivate());

        });

        Awaitility.await().until(() -> result.get() != null);
        assertThat(result.get()).contains(value.get(), "executor-thread")
                .doesNotContain(caller.get());
    }

    @Test
    @RunOnVertxContext
    @ActivateRequestContext
    @EnabledForJreRange(min = JRE.JAVA_21)
    void testNonBlockingToolInvocationFromVirtualThread() throws ExecutionException, InterruptedException {
        String uuid = uuidGenerator.get();
        AtomicReference<String> caller = new AtomicReference<>();
        var r = VirtualThreadsRecorder.getCurrent().submit(() -> {
            caller.set(Thread.currentThread().getName());
            return aiService.helloNonBlocking("abc", "hiNonBlocking")
                    .collect().asList().map(l -> String.join(" ", l)).await().indefinitely();
        }).get();

        // The blocking tool is executed on the same thread
        assertThat(r).contains(uuid, "quarkus-virtual-thread-")
                .contains(caller.get());
    }

    @Test
    @ActivateRequestContext
    void testUniToolInvocationFromWorkerThread() {
        String uuid = uuidGenerator.get();
        var r = aiService.helloUni("abc", "hiUni")
                .collect().asList().map(l -> String.join(" ", l)).await().indefinitely();
        assertThat(r).contains(uuid, Thread.currentThread().getName()); // We are invoked on the same thread
    }

    @Test
    @ActivateRequestContext
    void testUniToolInvocationFromEventLoop() {
        AtomicReference<String> value = new AtomicReference<>();
        AtomicReference<String> result = new AtomicReference<>();
        var ctxt = VertxContext.getOrCreateDuplicatedContext(vertx);
        ctxt.runOnContext(x -> {
            Arc.container().requestContext().activate();
            value.set(uuidGenerator.get());
            aiService.helloUni("abc", "hiUni")
                    .collect().asList().map(l -> String.join(" ", l))
                    .subscribeAsCompletionStage()
                    .thenAccept(result::set)
                    .whenComplete((r, t) -> Arc.container().requestContext().deactivate());

        });

        Awaitility.await().until(() -> result.get() != null);
        assertThat(result.get()).contains(value.get(), "executor-thread");
    }

    @Test
    @RunOnVertxContext
    @ActivateRequestContext
    @EnabledForJreRange(min = JRE.JAVA_21)
    void testUniToolInvocationFromVirtualThread() throws ExecutionException, InterruptedException {
        String uuid = uuidGenerator.get();
        AtomicReference<String> caller = new AtomicReference<>();
        var r = VirtualThreadsRecorder.getCurrent().submit(() -> {
            caller.set(Thread.currentThread().getName());
            return aiService.helloUni("abc", "hiUni")
                    .collect().asList().map(l -> String.join(" ", l)).await().indefinitely();
        }).get();

        // The blocking tool is executed on the same thread (synchronous emission)
        assertThat(r).contains(uuid, "quarkus-virtual-thread-")
                .contains(caller.get());
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    @RunOnVertxContext(runOnEventLoop = false)
    @ActivateRequestContext
    void testToolInvocationOnVirtualThread() {
        String uuid = uuidGenerator.get();
        var r = aiService.helloVirtualTools("abc", "hiVirtualThread")
                .collect().asList().map(l -> String.join(" ", l)).await().indefinitely();
        assertThat(r).contains(uuid, "quarkus-virtual-thread-");
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    @RunOnVertxContext
    @ActivateRequestContext
    void testToolInvocationOnVirtualThreadFromVirtualThread() throws ExecutionException, InterruptedException {
        String uuid = uuidGenerator.get();
        AtomicReference<String> caller = new AtomicReference<>();
        var r = VirtualThreadsRecorder.getCurrent().submit(() -> {
            caller.set(Thread.currentThread().getName());
            return aiService.helloVirtualTools("abc", "hiVirtualThread")
                    .collect().asList().map(l -> String.join(" ", l)).await().indefinitely();
        }).get();

        // At the moment, we create a virtual thread every time.
        assertThat(r).contains(uuid, "quarkus-virtual-thread-")
                .doesNotContain(caller.get());
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    @ActivateRequestContext
    void testToolInvocationOnVirtualThreadFromEventLoop() {
        AtomicReference<String> value = new AtomicReference<>();
        AtomicReference<String> result = new AtomicReference<>();
        var ctxt = VertxContext.getOrCreateDuplicatedContext(vertx);
        ctxt.runOnContext(x -> {
            Arc.container().requestContext().activate();
            value.set(uuidGenerator.get());
            aiService.helloVirtualTools("abc", "hiVirtualThread")
                    .collect().asList().map(l -> String.join(" ", l))
                    .subscribeAsCompletionStage().thenAccept(result::set)
                    .whenComplete((r, t) -> Arc.container().requestContext().deactivate());
        });

        Awaitility.await().until(() -> result.get() != null);
        assertThat(result.get()).contains(value.get(), "quarkus-virtual-thread-");
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface MyAiService {

        @ToolBox(BlockingTool.class)
        Multi<String> hello(@MemoryId String memoryId, @UserMessage String userMessageContainingTheToolId);

        @ToolBox(NonBlockingTool.class)
        Multi<String> helloNonBlocking(@MemoryId String memoryId, @UserMessage String userMessageContainingTheToolId);

        @ToolBox({ NonBlockingTool.class, BlockingTool.class })
        Multi<String> helloNonBlockingWithSwitch(@MemoryId String memoryId, @UserMessage String userMessageContainingTheToolId);

        @ToolBox(UniTool.class)
        Multi<String> helloUni(@MemoryId String memoryId, @UserMessage String userMessageContainingTheToolId);

        @ToolBox(VirtualTool.class)
        Multi<String> helloVirtualTools(@MemoryId String memoryId, @UserMessage String userMessageContainingTheToolId);
    }

    @Singleton
    public static class BlockingTool {
        @Inject
        UUIDGenerator uuidGenerator;

        @Tool
        public String hi() {
            return uuidGenerator.get() + " " + Thread.currentThread();
        }
    }

    @Singleton
    public static class NonBlockingTool {
        @Inject
        UUIDGenerator uuidGenerator;

        @Tool
        @NonBlocking
        public String hiNonBlocking() {
            return uuidGenerator.get() + " " + Thread.currentThread();
        }
    }

    @Singleton
    public static class UniTool {
        @Inject
        UUIDGenerator uuidGenerator;

        @Tool
        public Uni<String> hiUni() {
            return Uni.createFrom().item(() -> uuidGenerator.get() + " " + Thread.currentThread());
        }
    }

    @Singleton
    public static class VirtualTool {

        @Inject
        UUIDGenerator uuidGenerator;

        @Tool
        @RunOnVirtualThread
        public String hiVirtualThread() {
            return uuidGenerator.get() + " " + Thread.currentThread();
        }
    }

    public static class MyChatModelSupplier implements Supplier<StreamingChatLanguageModel> {

        @Override
        public StreamingChatLanguageModel get() {
            return new MyChatModel();
        }
    }

    public static class MyChatModel implements StreamingChatLanguageModel {

        @Override
        public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications,
                StreamingResponseHandler<AiMessage> handler) {
            if (messages.size() == 1) {
                // Only the user message, extract the tool id from it
                String text = ((dev.langchain4j.data.message.UserMessage) messages.get(0)).singleText();
                // Only the user message
                handler.onComplete(new Response<>(new AiMessage("cannot be blank", List.of(ToolExecutionRequest.builder()
                        .id("my-tool-" + text)
                        .name(text)
                        .arguments("{}")
                        .build())), new TokenUsage(0, 0), FinishReason.TOOL_EXECUTION));
            } else if (messages.size() == 3) {
                // user -> tool request -> tool response
                ToolExecutionResultMessage last = (ToolExecutionResultMessage) Lists.last(messages);
                handler.onNext("response: ");
                handler.onNext(last.text());
                handler.onComplete(new Response<>(new AiMessage(""), new TokenUsage(0, 0), FinishReason.STOP));

            } else {
                handler.onError(new RuntimeException("Invalid number of messages"));
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

    @RequestScoped
    public static class UUIDGenerator {
        private final String uuid = UUID.randomUUID().toString();

        public String get() {
            return uuid;
        }
    }
}
