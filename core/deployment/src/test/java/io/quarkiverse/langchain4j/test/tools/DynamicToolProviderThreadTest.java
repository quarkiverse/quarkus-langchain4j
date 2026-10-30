package io.quarkiverse.langchain4j.test.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import jakarta.enterprise.context.control.ActivateRequestContext;
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
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.common.annotation.NonBlocking;
import io.smallrye.mutiny.Multi;
import io.vertx.core.Context;
import io.vertx.core.Vertx;

/**
 * A {@linkplain ToolProvider#isDynamic() dynamic} tool provider may block, so the streaming AI services must not
 * re-evaluate it on the event loop the model delivers its responses on, even when the tools requested by the model can
 * run on the event loop.
 */
public class DynamicToolProviderThreadTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(StreamingAssistant.class, NonBlockingTools.class, DynamicToolProvider.class,
                            EventLoopStreamingChatModelSupplier.class));

    static final List<Boolean> providerCalledOnEventLoop = new CopyOnWriteArrayList<>();

    @Inject
    StreamingAssistant assistant;

    @BeforeEach
    void reset() {
        providerCalledOnEventLoop.clear();
    }

    @Test
    @ActivateRequestContext
    void multi() {
        List<String> tokens = assistant.multi("hello").collect().asList().await().atMost(Duration.ofSeconds(30));
        assertThat(String.join("", tokens)).isEqualTo("second tool says: 2");
        assertProviderNotCalledOnEventLoop();
    }

    @Test
    @ActivateRequestContext
    void tokenStream() throws Exception {
        CompletableFuture<ChatResponse> completion = new CompletableFuture<>();
        assistant.tokenStream("hello")
                .onPartialResponse(ignored -> {
                })
                .onCompleteResponse(completion::complete)
                .onError(completion::completeExceptionally)
                .start();
        assertThat(completion.get(30, TimeUnit.SECONDS).aiMessage().text()).isEqualTo("second tool says: 2");
        assertProviderNotCalledOnEventLoop();
    }

    private static void assertProviderNotCalledOnEventLoop() {
        assertThat(providerCalledOnEventLoop).hasSize(3).containsOnly(false);
    }

    // the ToolProvider is a bean, used by the AI service without being declared on it
    @RegisterAiService(streamingChatLanguageModelSupplier = EventLoopStreamingChatModelSupplier.class, tools = NonBlockingTools.class)
    public interface StreamingAssistant {
        Multi<String> multi(@UserMessage String message);

        TokenStream tokenStream(@UserMessage String message);
    }

    @Singleton
    public static class NonBlockingTools {
        @Tool
        @NonBlocking
        public String first() {
            return "1";
        }
    }

    @Singleton
    public static class DynamicToolProvider implements ToolProvider {
        @Override
        public ToolProviderResult provideTools(ToolProviderRequest request) {
            providerCalledOnEventLoop.add(Context.isOnEventLoopThread());
            ToolProviderResult.Builder result = ToolProviderResult.builder();
            boolean firstToolExecuted = request.messages().stream()
                    .anyMatch(m -> m instanceof ToolExecutionResultMessage t && t.toolName().equals("first"));
            if (firstToolExecuted) {
                result.add(ToolSpecification.builder().name("second").build(), (r, memoryId) -> "2");
            }
            return result.build();
        }

        @Override
        public boolean isDynamic() {
            return true;
        }
    }

    /**
     * Calls the non-blocking first tool, then the second one, then answers with the result of the second tool, always
     * on the event loop, as the Vert.x based model clients do.
     */
    public static class EventLoopStreamingChatModelSupplier implements Supplier<StreamingChatModel> {
        @Override
        public StreamingChatModel get() {
            Vertx vertx = Arc.container().instance(Vertx.class).get();
            return new StreamingChatModel() {
                @Override
                public void chat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
                    vertx.getOrCreateContext().runOnContext(v -> {
                        ChatMessage last = chatRequest.messages().get(chatRequest.messages().size() - 1);
                        if (last instanceof ToolExecutionResultMessage toolResult && toolResult.toolName().equals("second")) {
                            String text = "second tool says: " + toolResult.text();
                            handler.onPartialResponse(text);
                            handler.onCompleteResponse(ChatResponse.builder()
                                    .aiMessage(AiMessage.from(text))
                                    .finishReason(FinishReason.STOP)
                                    .build());
                            return;
                        }
                        String toolName = last instanceof ToolExecutionResultMessage ? "second" : "first";
                        handler.onCompleteResponse(ChatResponse.builder()
                                .aiMessage(AiMessage.from(ToolExecutionRequest.builder()
                                        .id(toolName + "-id").name(toolName).arguments("{}").build()))
                                .finishReason(FinishReason.TOOL_EXECUTION)
                                .build());
                    });
                }
            };
        }
    }
}
