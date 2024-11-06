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
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkiverse.langchain4j.runtime.aiservice.NoopChatMemory;
import io.quarkiverse.langchain4j.test.Lists;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.virtual.threads.VirtualThreadsRecorder;
import io.smallrye.common.annotation.NonBlocking;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;

public class ToolExecutionModelTest {

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
        var r = aiService.hello("abc", "hi - " + uuid);
        assertThat(r).contains(uuid, Thread.currentThread().getName()); // We are invoked on the same thread
    }

    @Test
    @ActivateRequestContext
    void testBlockingToolInvocationFromEventLoop() {
        String uuid = UUID.randomUUID().toString();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        vertx.getOrCreateContext().runOnContext(x -> {
            try {
                Arc.container().requestContext().activate();
                aiService.hello("abc", "hi - " + uuid);
            } catch (IllegalStateException e) {
                failure.set(e);
            } finally {
                Arc.container().requestContext().deactivate();
            }
        });

        Awaitility.await().until(() -> failure.get() != null);
        assertThat(failure.get()).hasMessageContaining("Cannot execute blocking tools on event loop thread");
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
                return aiService.hello("abc", "hi - " + uuid);
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
        var r = aiService.hello("abc", "hiNonBlocking - " + uuid);
        assertThat(r).contains(uuid, Thread.currentThread().getName()); // We are invoked on the same thread
    }

    @Test
    @ActivateRequestContext
    void testNonBlockingToolInvocationFromEventLoop() {
        String uuid = UUID.randomUUID().toString();
        AtomicReference<String> result = new AtomicReference<>();
        AtomicReference<String> caller = new AtomicReference<>();
        ;
        vertx.getOrCreateContext().runOnContext(x -> {
            try {
                caller.set(Thread.currentThread().getName());
                Arc.container().requestContext().activate();
                result.set(aiService.hello("abc", "hiNonBlocking - " + uuid));
            } finally {
                Arc.container().requestContext().deactivate();
            }
        });

        Awaitility.await().until(() -> result.get() != null);
        assertThat(result.get()).contains(uuid, caller.get());
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
                return aiService.hello("abc", "hiNonBlocking - " + uuid);
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
        var r = aiService.hello("abc", "hiUni - " + uuid);
        assertThat(r).contains(uuid, Thread.currentThread().getName()); // We are invoked on the same thread
    }

    @Test
    @ActivateRequestContext
    void testUniToolInvocationFromEventLoop() {
        String uuid = UUID.randomUUID().toString();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        vertx.getOrCreateContext().runOnContext(x -> {
            try {
                Arc.container().requestContext().activate();
                aiService.hello("abc", "hiUni - " + uuid);
            } catch (Exception e) {
                failure.set(e);
            } finally {
                Arc.container().requestContext().deactivate();
            }
        });

        Awaitility.await().until(() -> failure.get() != null);
        assertThat(failure.get()).hasMessageContaining("Cannot execute tools returning Uni on event loop thread");
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
                return aiService.hello("abc", "hiUni - " + uuid);
            } finally {
                Arc.container().requestContext().deactivate();
            }
        }).get();

        // The blocking tool is executed on the same thread
        assertThat(r).contains(uuid, "quarkus-virtual-thread-")
                .contains(caller.get());
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    @ActivateRequestContext
    void testToolInvocationOnVirtualThread() {
        String uuid = UUID.randomUUID().toString();
        var r = aiService.hello("abc", "hiVirtualThread - " + uuid);
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
                return aiService.hello("abc", "hiVirtualThread - " + uuid);
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
        vertx.getOrCreateContext().runOnContext(x -> {
            try {
                Arc.container().requestContext().activate();
                aiService.hello("abc", "hiVirtualThread - " + uuid);
            } catch (IllegalStateException e) {
                failure.set(e);
            } finally {
                Arc.container().requestContext().deactivate();
            }
        });

        Awaitility.await().until(() -> failure.get() != null);
        assertThat(failure.get()).hasMessageContaining("Cannot execute virtual thread tools on event loop thread");
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface MyAiService {

        @ToolBox(MyTool.class)
        String hello(@MemoryId String memoryId, @UserMessage String userMessageContainingTheToolId);
    }

    @Singleton
    public static class MyTool {
        @Tool
        public String hi(String m) {
            return m + " " + Thread.currentThread();
        }

        @Tool
        @NonBlocking
        public String hiNonBlocking(String m) {
            return m + " " + Thread.currentThread();
        }

        @Tool
        public Uni<String> hiUni(String m) {
            return Uni.createFrom().item(() -> m + " " + Thread.currentThread());
        }

        @Tool
        @RunOnVirtualThread
        public String hiVirtualThread(String m) {
            return m + " " + Thread.currentThread();
        }
    }

    public static class MyChatModelSupplier implements Supplier<ChatLanguageModel> {

        @Override
        public ChatLanguageModel get() {
            return new MyChatModel();
        }
    }

    public static class MyChatModel implements ChatLanguageModel {

        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages) {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
            if (messages.size() == 1) {
                // Only the user message, extract the tool id from it
                String text = ((dev.langchain4j.data.message.UserMessage) messages.get(0)).singleText();
                var segments = text.split(" - ");
                var toolId = segments[0];
                var content = segments[1];
                // Only the user message
                return new Response<>(new AiMessage("cannot be blank", List.of(ToolExecutionRequest.builder()
                        .id("my-tool-" + toolId)
                        .name(toolId)
                        .arguments("{\"m\":\"" + content + "\"}")
                        .build())), new TokenUsage(0, 0), FinishReason.TOOL_EXECUTION);
            } else if (messages.size() == 3) {
                // user -> tool request -> tool response
                ToolExecutionResultMessage last = (ToolExecutionResultMessage) Lists.last(messages);
                return new Response<>(AiMessage.from("response: " + last.text()));

            }
            return new Response<>(new AiMessage("Unexpected"));
        }
    }

    public static class MyMemoryProviderSupplier implements Supplier<ChatMemoryProvider> {
        @Override
        public ChatMemoryProvider get() {
            return new ChatMemoryProvider() {
                @Override
                public ChatMemory get(Object memoryId) {
                    return new NoopChatMemory();
                }
            };
        }
    }
}
