package io.quarkiverse.langchain4j.test.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
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

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import io.quarkiverse.langchain4j.CreatedAware;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkiverse.langchain4j.runtime.ContextLocals;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.common.annotation.NonBlocking;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.common.vertx.VertxContext;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Context;
import io.vertx.core.Vertx;

/**
 * The model responses are delivered on a Vert.x event loop, as they are by the Vert.x based model clients, and the
 * model asks for every available tool twice, so the second request is sent from the thread the first response was
 * handed to. Unless the invocation starts on a duplicated context, the first response is delivered on a root context,
 * since the first request is not sent from a Vert.x thread.
 */
public class ToolExecutionWithEventLoopDeliveryTest {

    private static final String NOT_DUPLICATED_CONTEXT = "not on a duplicated context";
    private static final String CALLER_LOCAL = "caller";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(MyAiService.class,
                    ClassLevelToolsAiService.class, ToolProviderAiService.class, EventLoopDeliveringChatModel.class));

    @Inject
    MyAiService aiService;

    @Inject
    ClassLevelToolsAiService classLevelToolsAiService;

    @Inject
    ToolProviderAiService toolProviderAiService;

    @Inject
    CallerState callerState;

    @Inject
    Vertx vertx;

    @Test
    @ActivateRequestContext
    void blockingToolRunsOnWorkerThreadWithTokenStream() throws Exception {
        String uuid = UUID.randomUUID().toString();

        String response = collect(aiService.blockingTokenStream(uuid, uuid));

        assertThat(response.split(uuid, -1)).hasSize(3);
        assertThat(response).contains("requested from executor-thread-")
                .doesNotContain("eventloop", NOT_DUPLICATED_CONTEXT);
    }

    @Test
    @ActivateRequestContext
    void uniToolRunsOnWorkerThreadWithTokenStream() throws Exception {
        String uuid = UUID.randomUUID().toString();

        String response = collect(aiService.uniTokenStream(uuid, uuid));

        assertThat(response.split(uuid, -1)).hasSize(3);
        assertThat(response).contains("requested from executor-thread-")
                .doesNotContain("eventloop", NOT_DUPLICATED_CONTEXT);
    }

    @Test
    @ActivateRequestContext
    void nonBlockingToolStaysOnEventLoopWithTokenStream() throws Exception {
        String uuid = UUID.randomUUID().toString();

        String response = collect(aiService.nonBlockingTokenStream(uuid, uuid));

        assertThat(response.split(uuid + " vert.x-eventloop-thread-", -1)).hasSize(3);
        assertThat(response).contains("requested from vert.x-eventloop-thread-");
    }

    @Test
    @ActivateRequestContext
    void nonBlockingAndBlockingToolsRunOnWorkerThreadWithTokenStream() throws Exception {
        String uuid = UUID.randomUUID().toString();

        String response = collect(aiService.nonBlockingAndBlockingTokenStream(uuid, uuid));

        assertThat(response.split(uuid + " executor-thread-", -1)).hasSize(5);
        assertThat(response).contains("requested from executor-thread-")
                .doesNotContain("eventloop", NOT_DUPLICATED_CONTEXT);
    }

    @Test
    @ActivateRequestContext
    @EnabledForJreRange(min = JRE.JAVA_21)
    void virtualThreadAndNonBlockingToolsRunFromVirtualThreadWithTokenStream() throws Exception {
        String uuid = UUID.randomUUID().toString();

        String response = collect(aiService.virtualThreadAndNonBlockingTokenStream(uuid, uuid));

        assertThat(response.split(uuid + " quarkus-virtual-thread-", -1)).hasSize(5);
        assertThat(response).contains("requested from quarkus-virtual-thread-")
                .doesNotContain("eventloop", NOT_DUPLICATED_CONTEXT);
    }

    @Test
    @ActivateRequestContext
    void virtualThreadAndBlockingToolsRunFromWorkerThreadWithTokenStream() throws Exception {
        String uuid = UUID.randomUUID().toString();

        String response = collect(aiService.virtualThreadAndBlockingTokenStream(uuid, uuid));

        assertThat(response.split(uuid, -1)).hasSize(5);
        assertThat(response).contains("requested from executor-thread-")
                .doesNotContain("eventloop", NOT_DUPLICATED_CONTEXT);
    }

    @Test
    @ActivateRequestContext
    void toolProviderToolRunsOnWorkerThreadWithTokenStream() throws Exception {
        String uuid = UUID.randomUUID().toString();

        String response = collect(toolProviderAiService.chat(uuid, uuid));

        assertThat(response.split(uuid, -1)).hasSize(3);
        assertThat(response).contains("requested from executor-thread-")
                .doesNotContain("eventloop", NOT_DUPLICATED_CONTEXT);
    }

    @Test
    @ActivateRequestContext
    void classLevelNonBlockingToolStaysOnEventLoopWithTokenStream() throws Exception {
        String uuid = UUID.randomUUID().toString();

        String response = collect(classLevelToolsAiService.chat(uuid, uuid));

        assertThat(response.split(uuid + " vert.x-eventloop-thread-", -1)).hasSize(3);
        assertThat(response).contains("requested from vert.x-eventloop-thread-");
        String blockingUuid = UUID.randomUUID().toString();
        assertRunsOnWorkerThread(collect(classLevelToolsAiService.blockingChat(blockingUuid, blockingUuid)));
    }

    @Test
    @ActivateRequestContext
    void programmaticNonBlockingToolStaysOnEventLoopWithTokenStream() throws Exception {
        String uuid = UUID.randomUUID().toString();
        ClassLevelToolsAiService programmaticAiService = AiServices.builder(ClassLevelToolsAiService.class)
                .streamingChatModel(new EventLoopDeliveringChatModel())
                .chatMemoryProvider(new MyMemoryProviderSupplier().get())
                .tools(new NonBlockingTool())
                .build();

        String response = collect(programmaticAiService.chat(uuid, uuid));

        assertThat(response).contains(uuid + " vert.x-eventloop-thread-", "requested from vert.x-eventloop-thread-")
                .doesNotContain("executor-thread-", "quarkus-virtual-thread-");
        String blockingUuid = UUID.randomUUID().toString();
        assertRunsOnWorkerThread(collect(programmaticAiService.blockingChat(blockingUuid, blockingUuid)));
    }

    /**
     * When the invocation starts on a duplicated context, as it does for an HTTP request, the model responses are
     * delivered on that context, and the tools must run on it too, so that they see its request scope and context locals.
     */
    @Test
    void toolsRunOnCallerDuplicatedContextWithTokenStream() throws Exception {
        String uuid = UUID.randomUUID().toString();
        Context callerContext = VertxContext.createNewDuplicatedContext(vertx.getOrCreateContext());

        String response = callerContext.executeBlocking(new Callable<String>() {
            @Override
            public String call() throws Exception {
                ManagedContext requestContext = Arc.container().requestContext();
                requestContext.activate();
                try {
                    callerState.setValue("request-" + uuid);
                    ContextLocals.put(CALLER_LOCAL, "local-" + uuid);
                    return collect(aiService.callerStateTokenStream(uuid, uuid));
                } finally {
                    requestContext.terminate();
                }
            }
        }, false).toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertThat(response.split("request-" + uuid, -1)).hasSize(3);
        assertThat(response.split("local-" + uuid, -1)).hasSize(3);
        assertThat(response).contains("requested from executor-thread-");
    }

    /**
     * Shows that the model of the AI service is wrapped, since a blocking tool would otherwise run on the event loop.
     */
    private static void assertRunsOnWorkerThread(String response) {
        assertThat(response).contains("requested from executor-thread-").doesNotContain("eventloop");
    }

    private static String collect(TokenStream tokenStream) throws Exception {
        StringBuilder streamed = new StringBuilder();
        CompletableFuture<String> done = new CompletableFuture<>();
        tokenStream
                .onPartialResponse(new Consumer<String>() {
                    @Override
                    public void accept(String token) {
                        streamed.append(token);
                    }
                })
                .onCompleteResponse(new Consumer<ChatResponse>() {
                    @Override
                    public void accept(ChatResponse response) {
                        done.complete(streamed.toString());
                    }
                })
                .onError(new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable error) {
                        done.completeExceptionally(error);
                    }
                })
                .start();
        return done.get(10, TimeUnit.SECONDS);
    }

    private static String executedOn(String m) {
        return m + " " + Thread.currentThread().getName()
                + (VertxContext.isOnDuplicatedContext() ? " on a duplicated context" : " " + NOT_DUPLICATED_CONTEXT);
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface MyAiService {

        @ToolBox(BlockingTool.class)
        TokenStream blockingTokenStream(@MemoryId String memoryId, @UserMessage String message);

        @ToolBox(UniTool.class)
        TokenStream uniTokenStream(@MemoryId String memoryId, @UserMessage String message);

        @ToolBox(NonBlockingTool.class)
        TokenStream nonBlockingTokenStream(@MemoryId String memoryId, @UserMessage String message);

        @ToolBox({ NonBlockingTool.class, BlockingTool.class })
        TokenStream nonBlockingAndBlockingTokenStream(@MemoryId String memoryId, @UserMessage String message);

        @ToolBox({ VirtualThreadTool.class, NonBlockingTool.class })
        TokenStream virtualThreadAndNonBlockingTokenStream(@MemoryId String memoryId, @UserMessage String message);

        @ToolBox({ VirtualThreadTool.class, BlockingTool.class })
        TokenStream virtualThreadAndBlockingTokenStream(@MemoryId String memoryId, @UserMessage String message);

        @ToolBox(CallerStateTool.class)
        TokenStream callerStateTokenStream(@MemoryId String memoryId, @UserMessage String message);
    }

    /**
     * The {@code chat} method uses the tools of the AI service, while the blocking tool of the {@code blockingChat} one
     * makes the {@code TokenStream} methods of the AI service use the wrapped model.
     */
    @CreatedAware
    @RegisterAiService(tools = NonBlockingTool.class, streamingChatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface ClassLevelToolsAiService {

        TokenStream chat(@MemoryId String memoryId, @UserMessage String message);

        @ToolBox(BlockingTool.class)
        TokenStream blockingChat(@MemoryId String memoryId, @UserMessage String message);
    }

    @RegisterAiService(toolProviderSupplier = MyToolProviderSupplier.class, streamingChatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface ToolProviderAiService {

        TokenStream chat(@MemoryId String memoryId, @UserMessage String message);
    }

    @Singleton
    public static class BlockingTool {
        @Tool
        public String hi(String m) {
            return executedOn(m);
        }
    }

    @Singleton
    public static class NonBlockingTool {
        @Tool
        @NonBlocking
        public String hiNonBlocking(String m) {
            return executedOn(m);
        }
    }

    @Singleton
    public static class VirtualThreadTool {
        @Tool
        @RunOnVirtualThread
        public String hiVirtualThread(String m) {
            return executedOn(m);
        }
    }

    @Singleton
    public static class UniTool {
        @Tool
        public Uni<String> hiUni(String m) {
            return Uni.createFrom().item(new Supplier<String>() {
                @Override
                public String get() {
                    return executedOn(m);
                }
            });
        }
    }

    @Singleton
    public static class CallerStateTool {

        @Inject
        CallerState callerState;

        @Tool
        public String hiCaller(String m) {
            return executedOn(m) + " " + callerState.getValue() + " " + ContextLocals.get(CALLER_LOCAL);
        }
    }

    @RequestScoped
    public static class CallerState {

        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    @Singleton
    public static class MyToolProviderSupplier implements Supplier<ToolProvider> {

        @Override
        public ToolProvider get() {
            return new ToolProvider() {
                @Override
                public ToolProviderResult provideTools(ToolProviderRequest request) {
                    ToolSpecification toolSpecification = ToolSpecification.builder()
                            .name("providedTool")
                            .description("A tool that is not known in advance")
                            .build();
                    ToolExecutor toolExecutor = new ToolExecutor() {
                        @Override
                        public String execute(ToolExecutionRequest toolExecutionRequest, Object memoryId) {
                            return executedOn(toolExecutionRequest.arguments());
                        }
                    };
                    return ToolProviderResult.builder().add(toolSpecification, toolExecutor).build();
                }
            };
        }
    }

    public static class MyChatModelSupplier implements Supplier<StreamingChatModel> {

        @Override
        public StreamingChatModel get() {
            return new EventLoopDeliveringChatModel();
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
