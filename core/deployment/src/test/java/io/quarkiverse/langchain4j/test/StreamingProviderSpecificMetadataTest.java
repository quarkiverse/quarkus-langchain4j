package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Supplier;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatEvent;
import io.quarkus.test.QuarkusExtensionTest;
import io.smallrye.mutiny.Multi;

/**
 * Model providers extend {@link ChatResponseMetadata} to expose provider-specific response details
 * (for example {@code OpenAiChatResponseMetadata#systemFingerprint()} or
 * {@code AnthropicChatResponseMetadata#rawServerSentEvents()}).
 * <p>
 * {@code ChatEvent.ChatCompletedEvent} must carry that instance through, like the upstream
 * {@code TokenStream} path does, instead of flattening it into a plain
 * {@link ChatResponseMetadata}.
 */
public class StreamingProviderSpecificMetadataTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyAiService.class, SimpleTool.class, MyChatModelSupplier.class,
                            MyMemoryProviderSupplier.class, CustomChatResponseMetadata.class));

    @Inject
    MyAiService aiService;

    @Test
    @ActivateRequestContext
    void providerMetadataIsPreserved() {
        ChatResponse response = complete(aiService.chat("mem-no-tool", "hello"));

        assertThat(response.metadata()).isInstanceOf(CustomChatResponseMetadata.class);
        assertThat(((CustomChatResponseMetadata) response.metadata()).providerDetail()).isEqualTo("detail-1");
        assertThat(response.metadata().id()).isEqualTo("id-1");
        assertThat(response.metadata().modelName()).isEqualTo("my-model");
        assertThat(response.metadata().finishReason()).isEqualTo(FinishReason.STOP);
        assertThat(response.metadata().tokenUsage()).isEqualTo(new TokenUsage(3, 7));
    }

    @Test
    @ActivateRequestContext
    void providerMetadataIsPreservedAfterToolExecution() {
        ChatResponse response = complete(aiService.chatWithTool("mem-tool", "hello"));

        // The metadata of the last response wins, the token usage is the sum over all round trips.
        assertThat(response.metadata()).isInstanceOf(CustomChatResponseMetadata.class);
        assertThat(((CustomChatResponseMetadata) response.metadata()).providerDetail()).isEqualTo("detail-2");
        assertThat(response.metadata().id()).isEqualTo("id-2");
        assertThat(response.metadata().tokenUsage()).isEqualTo(new TokenUsage(13, 27));
    }

    private ChatResponse complete(Multi<ChatEvent> stream) {
        List<ChatEvent> events = stream.collect().asList().await().indefinitely();

        return events.stream()
                .filter(e -> e instanceof ChatEvent.ChatCompletedEvent)
                .map(e -> ((ChatEvent.ChatCompletedEvent) e).getChatResponse())
                .findFirst()
                .orElseThrow(() -> new AssertionError("No ChatCompletedEvent was emitted"));
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface MyAiService {

        Multi<ChatEvent> chat(@MemoryId String memoryId, @UserMessage String message);

        @ToolBox(SimpleTool.class)
        Multi<ChatEvent> chatWithTool(@MemoryId String memoryId, @UserMessage String message);
    }

    @Singleton
    public static class SimpleTool {
        @Tool
        public String doSomething(String input) {
            return "result-" + input;
        }
    }

    public static class MyChatModelSupplier implements Supplier<StreamingChatModel> {
        @Override
        public StreamingChatModel get() {
            return new StreamingChatModel() {
                @Override
                public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
                    List<ChatMessage> messages = chatRequest.messages();
                    boolean toolsEnabled = chatRequest.parameters() != null
                            && chatRequest.parameters().toolSpecifications() != null
                            && !chatRequest.parameters().toolSpecifications().isEmpty();

                    if (toolsEnabled && messages.size() == 1) {
                        handler.onCompleteResponse(ChatResponse.builder()
                                .aiMessage(new AiMessage("", List.of(ToolExecutionRequest.builder()
                                        .id("call-1")
                                        .name("doSomething")
                                        .arguments("{\"input\":\"hello\"}")
                                        .build())))
                                .metadata(CustomChatResponseMetadata.builder()
                                        .providerDetail("detail-1")
                                        .id("id-1")
                                        .modelName("my-model")
                                        .tokenUsage(new TokenUsage(10, 20))
                                        .finishReason(FinishReason.TOOL_EXECUTION)
                                        .build())
                                .build());
                        return;
                    }

                    boolean firstRoundTrip = messages.size() == 1;
                    handler.onPartialResponse("Done!");
                    handler.onCompleteResponse(ChatResponse.builder()
                            .aiMessage(new AiMessage("Done!"))
                            .metadata(CustomChatResponseMetadata.builder()
                                    .providerDetail(firstRoundTrip ? "detail-1" : "detail-2")
                                    .id(firstRoundTrip ? "id-1" : "id-2")
                                    .modelName("my-model")
                                    .tokenUsage(new TokenUsage(3, 7))
                                    .finishReason(FinishReason.STOP)
                                    .build())
                            .build());
                }
            };
        }
    }

    public static class MyMemoryProviderSupplier implements Supplier<ChatMemoryProvider> {
        @Override
        public ChatMemoryProvider get() {
            return memoryId -> MessageWindowChatMemory.withMaxMessages(10);
        }
    }

    /**
     * Stands in for a provider-specific metadata type such as {@code OpenAiChatResponseMetadata}.
     */
    public static class CustomChatResponseMetadata extends ChatResponseMetadata {

        private final String providerDetail;

        private CustomChatResponseMetadata(Builder builder) {
            super(builder);
            this.providerDetail = builder.providerDetail;
        }

        public String providerDetail() {
            return providerDetail;
        }

        @Override
        public Builder toBuilder() {
            return ((Builder) super.toBuilder(builder())).providerDetail(providerDetail);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder extends ChatResponseMetadata.Builder<Builder> {

            private String providerDetail;

            public Builder providerDetail(String providerDetail) {
                this.providerDetail = providerDetail;
                return this;
            }

            @Override
            public CustomChatResponseMetadata build() {
                return new CustomChatResponseMetadata(this);
            }
        }
    }
}
