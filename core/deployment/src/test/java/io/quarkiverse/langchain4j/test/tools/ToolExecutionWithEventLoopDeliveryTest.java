package io.quarkiverse.langchain4j.test.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkiverse.langchain4j.test.Lists;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

/**
 * The model responses are delivered on a Vert.x event loop, as they are by the Vert.x based model clients.
 */
public class ToolExecutionWithEventLoopDeliveryTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(MyAiService.class,
                    EventLoopDeliveringChatModel.class, Lists.class));

    @Inject
    MyAiService aiService;

    @Test
    @ActivateRequestContext
    void blockingToolDoesNotRunOnEventLoopWithTokenStream() throws Exception {
        String uuid = UUID.randomUUID().toString();

        assertThat(collect(aiService.blockingTokenStream(uuid, uuid))).contains(uuid).doesNotContain("eventloop");
    }

    @Test
    @ActivateRequestContext
    void uniToolRunsWithTokenStream() throws Exception {
        String uuid = UUID.randomUUID().toString();

        assertThat(collect(aiService.uniTokenStream(uuid, uuid))).contains(uuid);
    }

    @Test
    @ActivateRequestContext
    void blockingToolDoesNotRunOnEventLoopWithMulti() {
        String uuid = UUID.randomUUID().toString();

        assertThat(collect(aiService.blockingMulti(uuid, uuid))).contains(uuid).doesNotContain("eventloop");
    }

    @Test
    @ActivateRequestContext
    void uniToolRunsOnEventLoopWithMulti() {
        String uuid = UUID.randomUUID().toString();

        assertThat(collect(aiService.uniMulti(uuid, uuid))).contains(uuid, "eventloop");
    }

    @Test
    @ActivateRequestContext
    void completionStageToolRunsOnEventLoopWithMulti() {
        String uuid = UUID.randomUUID().toString();

        assertThat(collect(aiService.completionStageMulti(uuid, uuid))).contains(uuid, "eventloop");
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

    private static String collect(Multi<String> multi) {
        return String.join("", multi.collect().asList().await().atMost(Duration.ofSeconds(10)));
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface MyAiService {

        @ToolBox(BlockingTool.class)
        TokenStream blockingTokenStream(@MemoryId String memoryId, @UserMessage String message);

        @ToolBox(UniTool.class)
        TokenStream uniTokenStream(@MemoryId String memoryId, @UserMessage String message);

        @ToolBox(BlockingTool.class)
        Multi<String> blockingMulti(@MemoryId String memoryId, @UserMessage String message);

        @ToolBox(UniTool.class)
        Multi<String> uniMulti(@MemoryId String memoryId, @UserMessage String message);

        @ToolBox(CompletionStageTool.class)
        Multi<String> completionStageMulti(@MemoryId String memoryId, @UserMessage String message);
    }

    @Singleton
    public static class BlockingTool {
        @Tool
        public String hi(String m) {
            return m + " " + Thread.currentThread().getName();
        }
    }

    @Singleton
    public static class UniTool {
        @Tool
        public Uni<String> hiUni(String m) {
            return Uni.createFrom().item(new Supplier<String>() {
                @Override
                public String get() {
                    return m + " " + Thread.currentThread().getName();
                }
            });
        }
    }

    @Singleton
    public static class CompletionStageTool {
        @Tool
        public CompletionStage<String> hiCompletionStage(String m) {
            return CompletableFuture.completedFuture(m + " " + Thread.currentThread().getName());
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
