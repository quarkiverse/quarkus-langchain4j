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

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
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
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;

/**
 * A {@linkplain ToolProvider#isDynamic() dynamic} tool provider must be re-evaluated before each LLM call of an AI service
 * invocation, so that it can provide tools depending on the conversation so far.
 */
public class DynamicToolProviderTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(BlockingAssistant.class, StreamingAssistant.class, DynamicToolProviderSupplier.class,
                            ChatModelSupplier.class, StreamingChatModelSupplier.class));

    static final List<List<String>> toolsSentToModel = new CopyOnWriteArrayList<>();
    static final List<ToolProviderRequest> toolProviderRequests = new CopyOnWriteArrayList<>();

    @Inject
    BlockingAssistant blockingAssistant;

    @Inject
    StreamingAssistant streamingAssistant;

    @BeforeEach
    void reset() {
        toolsSentToModel.clear();
        toolProviderRequests.clear();
    }

    @Test
    @ActivateRequestContext
    void blocking() {
        assertThat(blockingAssistant.chat("hello")).isEqualTo("second tool says: 2");
        assertDynamicProviderReEvaluated();
    }

    @Test
    @ActivateRequestContext
    void multi() {
        List<String> tokens = streamingAssistant.multi("hello").collect().asList().await().atMost(Duration.ofSeconds(30));
        assertThat(String.join("", tokens)).isEqualTo("second tool says: 2");
        assertDynamicProviderReEvaluated();
    }

    @Test
    @ActivateRequestContext
    void tokenStream() throws Exception {
        CompletableFuture<ChatResponse> completion = new CompletableFuture<>();
        streamingAssistant.tokenStream("hello")
                .onPartialResponse(ignored -> {
                })
                .onCompleteResponse(completion::complete)
                .onError(completion::completeExceptionally)
                .start();
        assertThat(completion.get(30, TimeUnit.SECONDS).aiMessage().text()).isEqualTo("second tool says: 2");
        assertDynamicProviderReEvaluated();
    }

    private static void assertDynamicProviderReEvaluated() {
        // the second tool only becomes available once the first one has been executed
        assertThat(toolsSentToModel).containsExactly(
                List.of("first"),
                List.of("first", "second"),
                List.of("first", "second"));
        // the provider is called before each LLM call, with the messages about to be sent
        assertThat(toolProviderRequests).hasSize(3);
        assertThat(toolProviderRequests).allSatisfy(r -> assertThat(r.userMessage().singleText()).isEqualTo("hello"));
        assertThat(toolProviderRequests).extracting(r -> r.messages().size()).containsExactly(1, 3, 5);
    }

    @RegisterAiService(chatLanguageModelSupplier = ChatModelSupplier.class, toolProviderSupplier = DynamicToolProviderSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    public interface BlockingAssistant {
        String chat(@UserMessage String message);
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = StreamingChatModelSupplier.class, toolProviderSupplier = DynamicToolProviderSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    public interface StreamingAssistant {
        Multi<String> multi(@UserMessage String message);

        TokenStream tokenStream(@UserMessage String message);
    }

    @Singleton
    public static class DynamicToolProviderSupplier implements Supplier<ToolProvider> {
        @Override
        public ToolProvider get() {
            return new ToolProvider() {
                @Override
                public ToolProviderResult provideTools(ToolProviderRequest request) {
                    toolProviderRequests.add(request);
                    ToolProviderResult.Builder result = ToolProviderResult.builder()
                            .add(ToolSpecification.builder().name("first").build(), (r, memoryId) -> "1");
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
            };
        }
    }

    /**
     * Calls the first tool, then the second one, then answers with the result of the second tool.
     */
    static ChatResponse respond(ChatRequest chatRequest) {
        toolsSentToModel.add(chatRequest.toolSpecifications().stream().map(ToolSpecification::name).toList());
        ChatMessage last = chatRequest.messages().get(chatRequest.messages().size() - 1);
        if (last instanceof ToolExecutionResultMessage toolResult) {
            if (toolResult.toolName().equals("second")) {
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from("second tool says: " + toolResult.text()))
                        .finishReason(FinishReason.STOP)
                        .build();
            }
            return callTool("second");
        }
        return callTool("first");
    }

    private static ChatResponse callTool(String name) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(ToolExecutionRequest.builder().id(name + "-id").name(name).arguments("{}").build()))
                .finishReason(FinishReason.TOOL_EXECUTION)
                .build();
    }

    public static class ChatModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest chatRequest) {
                    return respond(chatRequest);
                }
            };
        }
    }

    public static class StreamingChatModelSupplier implements Supplier<StreamingChatModel> {
        @Override
        public StreamingChatModel get() {
            return new StreamingChatModel() {
                @Override
                public void chat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
                    ChatResponse response = respond(chatRequest);
                    if (response.aiMessage().text() != null) {
                        handler.onPartialResponse(response.aiMessage().text());
                    }
                    handler.onCompleteResponse(response);
                }
            };
        }
    }
}
