package io.quarkiverse.langchain4j.test.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
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
import io.quarkus.virtual.threads.VirtualThreadsRecorder;
import io.smallrye.common.annotation.NonBlocking;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;

public class ToolExecutionModelWithStreamingTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(MyAiService.class, Lists.class));

    @Inject
    MyAiService aiService;

    @Inject
    Vertx vertx;

    @Test
    @ActivateRequestContext
    void testBlockingToolInvocationFromWorkerThread() {
        String uuid = UUID.randomUUID().toString();
        var r = aiService.hello("abc", "hi - " + uuid)
                .collect().asList().map(l -> String.join(" ", l)).await().indefinitely();
        assertThat(r).contains(uuid, Thread.currentThread().getName()); // We are invoked on the same thread
    }

    @Test
    @ActivateRequestContext
    void testBlockingToolInvocationFromEventLoop() {
        String uuid = UUID.randomUUID().toString();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<String> result = new AtomicReference<>();
        vertx.getOrCreateContext().runOnContext(x -> {
            try {
                Arc.container().requestContext().activate();
                aiService.hello("abc", "hi - " + uuid)
                        .collect().asList().map(l -> String.join(" ", l))
                        .subscribeAsCompletionStage()
                        .thenAccept(result::set);
            } catch (IllegalStateException e) {
                failure.set(e);
            } finally {
                Arc.container().requestContext().deactivate();
            }
        });

        // We would automatically detect this case and switch to a worker thread at subscription time.

        Awaitility.await().until(() -> failure.get() != null || result.get() != null);
        assertThat(failure.get()).isNull();
        assertThat(result.get()).doesNotContain("event", "loop")
                .contains(uuid, "executor-thread");
    }

    @Test
    @ActivateRequestContext
    @EnabledForJreRange(min = JRE.JAVA_21)
    void testBlockingToolInvocationFromVirtualThread() throws ExecutionException, InterruptedException {
        String uuid = UUID.randomUUID().toString();
        AtomicReference<String> caller = new AtomicReference<>();
        var r = VirtualThreadsRecorder.getCurrent().submit(() -> {
            try {
                Arc.container().requestContext().activate();
                caller.set(Thread.currentThread().getName());
                return aiService.hello("abc", "hi - " + uuid)
                        .collect().asList().map(l -> String.join(" ", l)).await().indefinitely();
            } finally {
                Arc.container().requestContext().deactivate();
            }
        }).get();

        // The blocking tool is executed on the same thread
        assertThat(r).contains(uuid, "quarkus-virtual-thread-")
                .contains(caller.get());
    }

    @Test
    @ActivateRequestContext
    void testNonBlockingToolInvocationFromWorkerThread() {
        String uuid = UUID.randomUUID().toString();
        var r = aiService.helloNonBlocking("abc", "hiNonBlocking - " + uuid)
                .collect().asList().map(l -> String.join(" ", l)).await().indefinitely();
        assertThat(r).contains(uuid, Thread.currentThread().getName()); // We are invoked on the same thread
    }

    @Test
    @ActivateRequestContext
    void testNonBlockingToolInvocationFromEventLoop() {
        String uuid = UUID.randomUUID().toString();
        AtomicReference<String> result = new AtomicReference<>();
        AtomicReference<String> caller = new AtomicReference<>();

        vertx.getOrCreateContext().runOnContext(x -> {
            try {
                caller.set(Thread.currentThread().getName());
                Arc.container().requestContext().activate();
                aiService.helloNonBlocking("abc", "hiNonBlocking - " + uuid)
                        .collect().asList().map(l -> String.join(" ", l))
                        .subscribeAsCompletionStage().thenAccept(result::set);
            } finally {
                Arc.container().requestContext().deactivate();
            }
        });

        Awaitility.await().until(() -> result.get() != null);
        assertThat(result.get()).contains(uuid, caller.get());
    }

    @Test
    @ActivateRequestContext
    void testNonBlockingToolInvocationFromEventLoopWhenWeSwitchToWorkerThread() {
        String uuid = UUID.randomUUID().toString();
        AtomicReference<String> result = new AtomicReference<>();
        AtomicReference<String> caller = new AtomicReference<>();

        vertx.getOrCreateContext().runOnContext(x -> {
            try {
                caller.set(Thread.currentThread().getName());
                Arc.container().requestContext().activate();
                aiService.helloNonBlockingWithSwitch("abc", "hiNonBlocking - " + uuid)
                        .collect().asList().map(l -> String.join(" ", l))
                        .subscribeAsCompletionStage().thenAccept(result::set);
            } finally {
                Arc.container().requestContext().deactivate();
            }
        });

        Awaitility.await().until(() -> result.get() != null);
        assertThat(result.get()).contains(uuid, "executor-thread")
                .doesNotContain(caller.get());
    }

    @Test
    @ActivateRequestContext
    @EnabledForJreRange(min = JRE.JAVA_21)
    void testNonBlockingToolInvocationFromVirtualThread() throws ExecutionException, InterruptedException {
        String uuid = UUID.randomUUID().toString();
        AtomicReference<String> caller = new AtomicReference<>();
        var r = VirtualThreadsRecorder.getCurrent().submit(() -> {
            try {
                Arc.container().requestContext().activate();
                caller.set(Thread.currentThread().getName());
                return aiService.helloNonBlocking("abc", "hiNonBlocking - " + uuid)
                        .collect().asList().map(l -> String.join(" ", l)).await().indefinitely();
            } finally {
                Arc.container().requestContext().deactivate();
            }
        }).get();

        // The blocking tool is executed on the same thread
        assertThat(r).contains(uuid, "quarkus-virtual-thread-")
                .contains(caller.get());
    }

    @Test
    @ActivateRequestContext
    void testUniToolInvocationFromWorkerThread() {
        String uuid = UUID.randomUUID().toString();
        var r = aiService.helloUni("abc", "hiUni - " + uuid)
                .collect().asList().map(l -> String.join(" ", l)).await().indefinitely();
        assertThat(r).contains(uuid, Thread.currentThread().getName()); // We are invoked on the same thread
    }

    @Test
    @ActivateRequestContext
    void testUniToolInvocationFromEventLoop() {
        String uuid = UUID.randomUUID().toString();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<String> result = new AtomicReference<>();
        vertx.getOrCreateContext().runOnContext(x -> {
            try {
                Arc.container().requestContext().activate();
                aiService.helloUni("abc", "hiUni - " + uuid)
                        .collect().asList().map(l -> String.join(" ", l))
                        .subscribeAsCompletionStage()
                        .thenAccept(result::set);
            } catch (Exception e) {
                failure.set(e);
            } finally {
                Arc.container().requestContext().deactivate();
            }
        });

        Awaitility.await().until(() -> failure.get() != null || result.get() != null);
        assertThat(failure.get()).isNull();
        assertThat(result.get()).contains(uuid, "executor-thread");
    }

    @Test
    @ActivateRequestContext
    @EnabledForJreRange(min = JRE.JAVA_21)
    void testUniToolInvocationFromVirtualThread() throws ExecutionException, InterruptedException {
        String uuid = UUID.randomUUID().toString();
        AtomicReference<String> caller = new AtomicReference<>();
        var r = VirtualThreadsRecorder.getCurrent().submit(() -> {
            try {
                Arc.container().requestContext().activate();
                caller.set(Thread.currentThread().getName());
                return aiService.helloUni("abc", "hiUni - " + uuid)
                        .collect().asList().map(l -> String.join(" ", l)).await().indefinitely();
            } finally {
                Arc.container().requestContext().deactivate();
            }
        }).get();

        // The blocking tool is executed on the same thread (synchronous emission)
        assertThat(r).contains(uuid, "quarkus-virtual-thread-")
                .contains(caller.get());
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    @ActivateRequestContext
    void testToolInvocationOnVirtualThread() {
        String uuid = UUID.randomUUID().toString();
        var r = aiService.helloVirtualTools("abc", "hiVirtualThread - " + uuid)
                .collect().asList().map(l -> String.join(" ", l)).await().indefinitely();
        assertThat(r).contains(uuid, "quarkus-virtual-thread-");
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    void testToolInvocationOnVirtualThreadFromVirtualThread() throws ExecutionException, InterruptedException {
        String uuid = UUID.randomUUID().toString();
        AtomicReference<String> caller = new AtomicReference<>();
        var r = VirtualThreadsRecorder.getCurrent().submit(() -> {
            try {
                Arc.container().requestContext().activate();
                caller.set(Thread.currentThread().getName());
                return aiService.helloVirtualTools("abc", "hiVirtualThread - " + uuid)
                        .collect().asList().map(l -> String.join(" ", l)).await().indefinitely();
            } finally {
                Arc.container().requestContext().deactivate();
            }
        }).get();

        // At the moment, we create a virtual thread every time.
        assertThat(r).contains(uuid, "quarkus-virtual-thread-")
                .doesNotContain(caller.get());
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    void testToolInvocationOnVirtualThreadFromEventLoop() {
        String uuid = UUID.randomUUID().toString();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<String> result = new AtomicReference<>();
        vertx.getOrCreateContext().runOnContext(x -> {
            try {
                Arc.container().requestContext().activate();
                aiService.helloVirtualTools("abc", "hiVirtualThread - " + uuid)
                        .collect().asList().map(l -> String.join(" ", l))
                        .subscribeAsCompletionStage().thenAccept(result::set);
            } catch (IllegalStateException e) {
                failure.set(e);
            } finally {
                Arc.container().requestContext().deactivate();
            }
        });

        Awaitility.await().until(() -> failure.get() != null || result.get() != null);
        assertThat(failure.get()).isNull();
        assertThat(result.get()).contains(uuid, "quarkus-virtual-thread-");
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
        @Tool
        public String hi(String m) {
            return m + " " + Thread.currentThread();
        }
    }

    @Singleton
    public static class NonBlockingTool {
        @Tool
        @NonBlocking
        public String hiNonBlocking(String m) {
            return m + " " + Thread.currentThread();
        }
    }

    @Singleton
    public static class UniTool {
        @Tool
        public Uni<String> hiUni(String m) {
            return Uni.createFrom().item(() -> m + " " + Thread.currentThread());
        }
    }

    @Singleton
    public static class VirtualTool {

        @Tool
        @RunOnVirtualThread
        public String hiVirtualThread(String m) {
            return m + " " + Thread.currentThread();
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
                var segments = text.split(" - ");
                var toolId = segments[0];
                var content = segments[1];
                // Only the user message
                handler.onComplete(new Response<>(new AiMessage("cannot be blank", List.of(ToolExecutionRequest.builder()
                        .id("my-tool-" + toolId)
                        .name(toolId)
                        .arguments("{\"m\":\"" + content + "\"}")
                        .build())), new TokenUsage(0, 0), FinishReason.TOOL_EXECUTION));
            } else if (messages.size() == 3) {
                // user -> tool request -> tool response
                ToolExecutionResultMessage last = (ToolExecutionResultMessage) Lists.last(messages);
                handler.onNext("response: ");
                handler.onNext(last.text());
                handler.onComplete(new Response<>(new AiMessage(""), new TokenUsage(0, 0), FinishReason.STOP));

            }
            handler.onError(new RuntimeException("Invalid number of messages"));
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
