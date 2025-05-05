package io.quarkiverse.langchain4j.test.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
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

public class ToolExecutionModelWithStreamingUnsupportedTest {

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
        assertThatThrownBy(() -> aiService.hello("abc", "hi - " + uuid)
                .collect().asList().map(l -> String.join(" ", l)).await().indefinitely())
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("tools", "supported");
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
                        .whenComplete((r, t) -> {
                            if (t != null) {
                                failure.set(t);
                            } else {
                                result.set(r);
                            }
                        });
            } catch (IllegalStateException e) {
                failure.set(e);
            } finally {
                Arc.container().requestContext().deactivate();
            }
        });

        Awaitility.await().until(() -> failure.get() != null || result.get() != null);
        assertThat(failure.get()).hasMessageContaining("tools", "supported");
        assertThat(result.get()).isNull();
    }

    @Test
    @ActivateRequestContext
    @EnabledForJreRange(min = JRE.JAVA_21)
    void testBlockingToolInvocationFromVirtualThread() throws ExecutionException, InterruptedException {
        String uuid = UUID.randomUUID().toString();
        var r = VirtualThreadsRecorder.getCurrent().submit(() -> {
            try {
                Arc.container().requestContext().activate();
                return aiService.hello("abc", "hi - " + uuid)
                        .collect().asList().map(l -> String.join(" ", l)).await().indefinitely();
            } catch (Exception e) {
                return e.getMessage();
            } finally {
                Arc.container().requestContext().deactivate();
            }
        }).get();

        assertThat(r).contains("tools", "supported");
    }

    @Test
    @ActivateRequestContext
    void testNonBlockingToolInvocationFromWorkerThread() {
        String uuid = UUID.randomUUID().toString();
        assertThatThrownBy(() -> aiService.helloNonBlocking("abc", "hiNonBlocking - " + uuid)
                .collect().asList().map(l -> String.join(" ", l)).await().indefinitely())
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("tools", "supported");
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
                        .subscribeAsCompletionStage()
                        .whenComplete((r, t) -> {
                            if (t != null) {
                                result.set(t.getMessage());
                            } else {
                                result.set(r);
                            }
                        });
            } finally {
                Arc.container().requestContext().deactivate();
            }
        });

        Awaitility.await().until(() -> result.get() != null);
        assertThat(result.get()).contains("tools", "supported");
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
                        .subscribeAsCompletionStage().whenComplete((s, t) -> {
                            if (t != null) {
                                result.set(t.getMessage());
                            } else {
                                result.set(s);
                            }
                        });
            } finally {
                Arc.container().requestContext().deactivate();
            }
        });

        Awaitility.await().until(() -> result.get() != null);
        assertThat(result.get()).contains("tools", "supported");
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
            } catch (Exception e) {
                return e.getMessage();
            } finally {
                Arc.container().requestContext().deactivate();
            }
        }).get();

        assertThat(r).contains("tools", "supported");
    }

    @Test
    @ActivateRequestContext
    void testUniToolInvocationFromWorkerThread() {
        String uuid = UUID.randomUUID().toString();
        assertThatThrownBy(() -> aiService.helloUni("abc", "hiUni - " + uuid)
                .collect().asList().map(l -> String.join(" ", l)).await().indefinitely())
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("tools", "supported");
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
                        .whenComplete((r, t) -> {
                            if (t != null) {
                                failure.set(t);
                            } else {
                                result.set(r);
                            }
                        });
            } catch (Exception e) {
                failure.set(e);
            } finally {
                Arc.container().requestContext().deactivate();
            }
        });

        Awaitility.await().until(() -> failure.get() != null || result.get() != null);
        assertThat(failure.get()).hasMessageContaining("tools", "supported");
        assertThat(result.get()).isNull();
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
            } catch (Exception e) {
                return e.getMessage();
            } finally {
                Arc.container().requestContext().deactivate();
            }
        }).get();

        assertThat(r).contains("tools", "supported");
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    @ActivateRequestContext
    void testToolInvocationOnVirtualThread() {
        String uuid = UUID.randomUUID().toString();
        assertThatThrownBy(() -> aiService.helloVirtualTools("abc", "hiVirtualThread - " + uuid)
                .collect().asList().map(l -> String.join(" ", l)).await().indefinitely())
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("tools", "supported");
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
            } catch (Exception e) {
                return e.getMessage();
            } finally {
                Arc.container().requestContext().deactivate();
            }
        }).get();

        assertThat(r).contains("tools", "supported");
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
                        .subscribeAsCompletionStage().whenComplete((r, t) -> {
                            if (t != null) {
                                failure.set(t);
                            } else {
                                result.set(r);
                            }
                        });
            } catch (IllegalStateException e) {
                failure.set(e);
            } finally {
                Arc.container().requestContext().deactivate();
            }
        });

        Awaitility.await().until(() -> failure.get() != null || result.get() != null);
        assertThat(failure.get()).hasMessageContaining("tools", "supported");
        assertThat(result.get()).isNull();
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

    public static class MyChatModelSupplier implements Supplier<StreamingChatModel> {

        @Override
        public StreamingChatModel get() {
            return new MyChatModel();
        }
    }

    public static class MyChatModel implements StreamingChatModel {

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            throw new UnsupportedFeatureException("Unsupported tools");
        }

        // The method supported tools is not implemented -> the default implementation is used and fails.

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
