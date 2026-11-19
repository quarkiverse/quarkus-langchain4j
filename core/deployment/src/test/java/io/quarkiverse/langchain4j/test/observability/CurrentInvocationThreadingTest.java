package io.quarkiverse.langchain4j.test.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.observability.api.event.AiServiceRequestIssuedEvent;
import dev.langchain4j.observability.api.event.AiServiceStartedEvent;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusExtensionTest;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

/**
 * The id of the AI service invocation is kept in a thread-local only while the extension calls code that has no
 * parameter for it (a chat model, a tool, an AI service invoked by a tool). These tests check that it survives the
 * thread switches made while serving an invocation, and that when it is lost it is {@code null}, never the id of
 * another invocation.
 * <p>
 * The streaming model delivers its responses on a Vert.x event loop, like the model clients based on the Vert.x HTTP
 * client do.
 */
public class CurrentInvocationThreadingTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    @Inject
    MultiOuter multiOuter;

    @Inject
    TokenStreamOuter tokenStreamOuter;

    @Inject
    SyncInner syncInner;

    @Inject
    VirtualThreadToolOuter virtualThreadToolOuter;

    @Inject
    ConcurrentToolsOuter concurrentToolsOuter;

    @Inject
    UniToolOuter uniToolOuter;

    @Inject
    MultiCallingToolOuter multiCallingToolOuter;

    @Inject
    Vertx vertx;

    @Inject
    Recorder recorder;

    @BeforeEach
    void clear() {
        recorder.clear();
    }

    @Test
    void multiWithResponsesOnTheEventLoopAndToolOnAWorker() {
        assertThat(multiOuter.chat("hello").collect().asList().await().atMost(Duration.ofSeconds(10)))
                .containsExactly("Hi!");

        assertLinkedThroughTheTool(MultiOuter.class);
        // the thread switches did happen
        assertDeliveredOnTheEventLoop();
        assertThat(recorder.singleToolThread().eventLoop()).isFalse();
    }

    @Test
    void tokenStreamWithResponsesOnTheEventLoop() throws Exception {
        CompletableFuture<ChatResponse> done = new CompletableFuture<>();
        tokenStreamOuter.chat("hello")
                .onPartialResponse(new Consumer<String>() {
                    @Override
                    public void accept(String s) {
                    }
                })
                .onCompleteResponse(new Consumer<ChatResponse>() {
                    @Override
                    public void accept(ChatResponse response) {
                        done.complete(response);
                    }
                })
                .onError(new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable error) {
                        done.completeExceptionally(error);
                    }
                })
                .start();
        assertThat(done.get(10, TimeUnit.SECONDS).aiMessage().text()).isEqualTo("Hi!");

        assertLinkedThroughTheTool(TokenStreamOuter.class);
        assertDeliveredOnTheEventLoop();
    }

    @Test
    void multiInvokedFromTheEventLoop() throws Exception {
        CompletableFuture<List<String>> result = new CompletableFuture<>();
        CompletableFuture<Boolean> callerOnEventLoop = new CompletableFuture<>();
        vertx.getOrCreateContext().runOnContext(new Handler<Void>() {
            @Override
            public void handle(Void ignored) {
                callerOnEventLoop.complete(Context.isOnEventLoopThread());
                multiOuter.chat("hello").collect().asList().subscribe().with(new Consumer<List<String>>() {
                    @Override
                    public void accept(List<String> items) {
                        result.complete(items);
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable error) {
                        result.completeExceptionally(error);
                    }
                });
            }
        });
        assertThat(callerOnEventLoop.get(10, TimeUnit.SECONDS)).isTrue();
        assertThat(result.get(10, TimeUnit.SECONDS)).containsExactly("Hi!");

        assertLinkedThroughTheTool(MultiOuter.class);
        assertThat(recorder.singleToolThread().eventLoop()).isFalse();
    }

    @Test
    void toolRunningOnAVirtualThread() {
        assertThat(virtualThreadToolOuter.chat("hello")).isEqualTo("Hi!");

        assertLinkedThroughTheTool(VirtualThreadToolOuter.class);
        assertThat(recorder.singleToolThread().virtual()).isTrue();
    }

    @Test
    void toolsExecutedConcurrently() {
        assertThat(concurrentToolsOuter.chat("hello")).isEqualTo("Hi!");

        InvocationContext outer = recorder.single(ConcurrentToolsOuter.class);
        List<InvocationContext> inners = recorder.all(SyncInner.class);
        assertThat(inners).hasSize(2);
        for (InvocationContext inner : inners) {
            assertThat(inner.parentInvocationId()).isEqualTo(outer.invocationId());
        }
        // both tools ran on a thread of the tool executor, not on the caller thread
        assertThat(recorder.toolThreads).hasSize(2);
        for (ThreadInfo toolThread : recorder.toolThreads) {
            assertThat(toolThread.name()).isNotEqualTo(Thread.currentThread().getName());
        }
        // the outer model calls carry the outer id, the inner ones their own
        assertThat(recorder.modelRequestIds).containsExactlyInAnyOrder(outer.invocationId(),
                inners.get(0).invocationId(), inners.get(1).invocationId(), outer.invocationId());
        assertThat(recorder.modelResponseIds).containsExactlyInAnyOrderElementsOf(recorder.modelRequestIds);
    }

    @Test
    void nothingIsLeftOnTheThreadsAfterAnInvocation() throws Exception {
        assertThat(multiOuter.chat("hello").collect().asList().await().atMost(Duration.ofSeconds(10)))
                .containsExactly("Hi!");
        UUID outerId = recorder.single(MultiOuter.class).invocationId();

        // on the caller thread
        assertThat(syncInner.chat("after")).isEqualTo("Hi!");
        // on the event loop that delivered the responses of the outer invocation
        CompletableFuture<String> onEventLoop = new CompletableFuture<>();
        StreamingModel.eventLoopContext().runOnContext(new Handler<Void>() {
            @Override
            public void handle(Void ignored) {
                onEventLoop.complete(syncInner.chat("after"));
            }
        });
        assertThat(onEventLoop.get(10, TimeUnit.SECONDS)).isEqualTo("Hi!");

        List<InvocationContext> inners = recorder.all(SyncInner.class);
        // the one invoked by the tool, and the two invoked after the outer invocation
        assertThat(inners).hasSize(3);
        assertThat(inners.get(0).parentInvocationId()).isEqualTo(outerId);
        assertThat(inners.get(1).parentInvocationId()).isNull();
        assertThat(inners.get(2).parentInvocationId()).isNull();
    }

    @Test
    void aiServiceInvokedAfterAThreadSwitchMadeByTheToolHasNoParent() {
        assertThat(uniToolOuter.chat("hello")).isEqualTo("Hi!");

        InvocationContext inner = recorder.single(SyncInner.class);
        // the tool switched threads itself (emitOn), so the link to the outer invocation is lost: the parent is
        // missing, not wrong
        assertThat(inner.parentInvocationId()).isNull();
        // the model call of the inner invocation is still tagged with its own id
        assertThat(recorder.modelRequestIds).contains(inner.invocationId());
        assertThat(recorder.singleToolThread().eventLoop()).isFalse();
    }

    @Test
    void multiSubscribedFromATool() {
        assertThat(multiCallingToolOuter.chat("hello")).isEqualTo("Hi!");

        InvocationContext outer = recorder.single(MultiCallingToolOuter.class);
        InvocationContext inner = recorder.single(AuditedMultiInner.class);
        InvocationContext audit = recorder.single(AuditService.class);
        assertThat(inner.parentInvocationId()).isEqualTo(outer.invocationId());
        // the observer of the inner invocation's events is not part of the work of the outer invocation, so an AI
        // service it invokes has no parent, like when the inner AI service returns a String
        assertThat(audit.parentInvocationId()).isNull();
        // the audit call is made when the inner request is issued, before the inner model call
        assertThat(recorder.modelRequestIds).containsExactly(outer.invocationId(), audit.invocationId(),
                inner.invocationId(), outer.invocationId());
    }

    private void assertDeliveredOnTheEventLoop() {
        assertThat(recorder.streamingModelResponseThreads).isNotEmpty();
        for (ThreadInfo thread : recorder.streamingModelResponseThreads) {
            assertThat(thread.eventLoop()).as(thread.name()).isTrue();
        }
    }

    private void assertLinkedThroughTheTool(Class<?> outerService) {
        InvocationContext outer = recorder.single(outerService);
        InvocationContext inner = recorder.single(SyncInner.class);
        assertThat(outer.parentInvocationId()).isNull();
        assertThat(inner.parentInvocationId()).isEqualTo(outer.invocationId());
        // the outer call asking for the tool, the inner call made by the tool, the outer call with the tool result
        assertThat(recorder.modelRequestIds).containsExactly(outer.invocationId(), inner.invocationId(),
                outer.invocationId());
        assertThat(recorder.modelResponseIds).containsExactlyInAnyOrder(outer.invocationId(), inner.invocationId(),
                outer.invocationId());
    }

    // ---- AI services

    @ApplicationScoped
    @RegisterAiService(streamingChatLanguageModelSupplier = StreamingModelSupplier.class, tools = InnerTool.class)
    public interface MultiOuter {
        Multi<String> chat(@UserMessage String message);
    }

    @ApplicationScoped
    @RegisterAiService(streamingChatLanguageModelSupplier = StreamingModelSupplier.class, tools = InnerTool.class)
    public interface TokenStreamOuter {
        TokenStream chat(@UserMessage String message);
    }

    @ApplicationScoped
    @RegisterAiService(chatLanguageModelSupplier = ChatModelSupplier.class)
    public interface SyncInner {
        String chat(@UserMessage String message);
    }

    @ApplicationScoped
    @RegisterAiService(chatLanguageModelSupplier = ChatModelSupplier.class, tools = VirtualThreadTool.class)
    public interface VirtualThreadToolOuter {
        String chat(@UserMessage String message);
    }

    @ApplicationScoped
    @RegisterAiService(chatLanguageModelSupplier = ChatModelSupplier.class, tools = TwoTools.class, executeToolsConcurrently = true)
    public interface ConcurrentToolsOuter {
        String chat(@UserMessage String message);
    }

    @ApplicationScoped
    @RegisterAiService(chatLanguageModelSupplier = ChatModelSupplier.class, tools = UniTool.class)
    public interface UniToolOuter {
        String chat(@UserMessage String message);
    }

    @ApplicationScoped
    @RegisterAiService(chatLanguageModelSupplier = ChatModelSupplier.class, tools = MultiCallingTool.class)
    public interface MultiCallingToolOuter {
        String chat(@UserMessage String message);
    }

    @ApplicationScoped
    @RegisterAiService(streamingChatLanguageModelSupplier = StreamingModelSupplier.class)
    public interface AuditedMultiInner {
        Multi<String> chat(@UserMessage String message);
    }

    @ApplicationScoped
    @RegisterAiService(chatLanguageModelSupplier = ChatModelSupplier.class)
    public interface AuditService {
        String chat(@UserMessage String message);
    }

    // ---- tools

    @ApplicationScoped
    public static class InnerTool {

        @Inject
        SyncInner inner;

        @Inject
        Recorder recorder;

        @Tool
        public String callInner() {
            recorder.toolThreads.add(ThreadInfo.current());
            return inner.chat("from the tool");
        }
    }

    @ApplicationScoped
    public static class VirtualThreadTool {

        @Inject
        SyncInner inner;

        @Inject
        Recorder recorder;

        @Tool
        @RunOnVirtualThread
        public String callInner() {
            recorder.toolThreads.add(ThreadInfo.current());
            return inner.chat("from the tool");
        }
    }

    @ApplicationScoped
    public static class TwoTools {

        @Inject
        SyncInner inner;

        @Inject
        Recorder recorder;

        @Tool
        public String callInnerA() {
            recorder.toolThreads.add(ThreadInfo.current());
            return inner.chat("from tool A");
        }

        @Tool
        public String callInnerB() {
            recorder.toolThreads.add(ThreadInfo.current());
            return inner.chat("from tool B");
        }
    }

    @ApplicationScoped
    public static class UniTool {

        @Inject
        SyncInner inner;

        @Inject
        Recorder recorder;

        @Tool
        public Uni<String> callInner() {
            recorder.toolThreads.add(ThreadInfo.current());
            return Uni.createFrom().item("from the tool")
                    .emitOn(Infrastructure.getDefaultWorkerPool())
                    .map(new Function<String, String>() {
                        @Override
                        public String apply(String message) {
                            return inner.chat(message);
                        }
                    });
        }
    }

    @ApplicationScoped
    public static class MultiCallingTool {

        @Inject
        AuditedMultiInner inner;

        @Tool
        public String callInner() {
            return String.join("", inner.chat("from the tool").collect().asList().await().atMost(Duration.ofSeconds(10)));
        }
    }

    // ---- recording

    public record ThreadInfo(String name, boolean eventLoop, boolean virtual) {

        static ThreadInfo current() {
            return new ThreadInfo(Thread.currentThread().getName(), Context.isOnEventLoopThread(),
                    Thread.currentThread().isVirtual());
        }
    }

    @Singleton
    public static class Recorder {

        final List<InvocationContext> started = new CopyOnWriteArrayList<>();
        final List<UUID> modelRequestIds = new CopyOnWriteArrayList<>();
        final List<UUID> modelResponseIds = new CopyOnWriteArrayList<>();
        final List<ThreadInfo> streamingModelResponseThreads = new CopyOnWriteArrayList<>();
        final List<ThreadInfo> toolThreads = new CopyOnWriteArrayList<>();

        @Inject
        AuditService auditService;

        void onStarted(@Observes AiServiceStartedEvent event) {
            started.add(event.invocationContext());
        }

        void onRequestIssued(@Observes AiServiceRequestIssuedEvent event) {
            if (AuditedMultiInner.class.getName().equals(event.invocationContext().interfaceName())) {
                auditService.chat("audit");
            }
        }

        InvocationContext single(Class<?> aiService) {
            List<InvocationContext> contexts = all(aiService);
            assertThat(contexts).hasSize(1);
            return contexts.get(0);
        }

        ThreadInfo singleToolThread() {
            assertThat(toolThreads).hasSize(1);
            return toolThreads.get(0);
        }

        List<InvocationContext> all(Class<?> aiService) {
            List<InvocationContext> result = new ArrayList<>();
            for (InvocationContext context : started) {
                if (aiService.getName().equals(context.interfaceName())) {
                    result.add(context);
                }
            }
            return result;
        }

        void clear() {
            started.clear();
            modelRequestIds.clear();
            modelResponseIds.clear();
            streamingModelResponseThreads.clear();
            toolThreads.clear();
        }
    }

    static ChatModelListener listener(boolean streaming) {
        return new ChatModelListener() {
            @Override
            public void onRequest(ChatModelRequestContext requestContext) {
                Arc.container().instance(Recorder.class).get().modelRequestIds.add(requestContext.invocationId());
            }

            @Override
            public void onResponse(ChatModelResponseContext responseContext) {
                Recorder recorder = Arc.container().instance(Recorder.class).get();
                recorder.modelResponseIds.add(responseContext.invocationId());
                if (streaming) {
                    recorder.streamingModelResponseThreads.add(ThreadInfo.current());
                }
            }
        };
    }

    // ---- models: the first call of an invocation that has tools asks for all of them, any other call answers

    static AiMessage answer(ChatRequest request) {
        List<ToolSpecification> tools = request.toolSpecifications();
        List<ChatMessage> messages = request.messages();
        boolean answersToolResults = messages.get(messages.size() - 1) instanceof ToolExecutionResultMessage;
        if (tools == null || tools.isEmpty() || answersToolResults) {
            return AiMessage.from("Hi!");
        }
        List<ToolExecutionRequest> toolCalls = new ArrayList<>();
        for (ToolSpecification tool : tools) {
            toolCalls.add(ToolExecutionRequest.builder().id(tool.name()).name(tool.name()).arguments("{}").build());
        }
        return AiMessage.from(toolCalls);
    }

    public static class ChatModelSupplier implements Supplier<ChatModel> {

        @Override
        public ChatModel get() {
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest request) {
                    return ChatResponse.builder().aiMessage(answer(request)).build();
                }

                @Override
                public List<ChatModelListener> listeners() {
                    return List.of(listener(false));
                }
            };
        }
    }

    public static class StreamingModelSupplier implements Supplier<StreamingChatModel> {

        @Override
        public StreamingChatModel get() {
            return new StreamingModel();
        }
    }

    public static class StreamingModel implements StreamingChatModel {

        private static volatile Context eventLoopContext;

        static synchronized Context eventLoopContext() {
            if (eventLoopContext == null) {
                eventLoopContext = Arc.container().instance(Vertx.class).get().getOrCreateContext();
            }
            return eventLoopContext;
        }

        @Override
        public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
            AiMessage aiMessage = answer(request);
            // deliver the response on the event loop, like the Vert.x based model clients
            eventLoopContext().runOnContext(new Handler<Void>() {
                @Override
                public void handle(Void ignored) {
                    if (aiMessage.text() != null) {
                        handler.onPartialResponse(aiMessage.text());
                    }
                    handler.onCompleteResponse(ChatResponse.builder().aiMessage(aiMessage).build());
                }
            });
        }

        @Override
        public List<ChatModelListener> listeners() {
            return List.of(listener(true));
        }
    }
}
