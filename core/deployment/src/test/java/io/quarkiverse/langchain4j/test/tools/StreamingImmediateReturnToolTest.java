package io.quarkiverse.langchain4j.test.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkus.test.QuarkusExtensionTest;
import io.smallrye.mutiny.Multi;

/**
 * {@code @Tool(returnBehavior = IMMEDIATE)} must terminate the tool calling loop for streaming
 * AI services too, exactly like it does for the blocking ones and like upstream LangChain4j does
 * in {@code AiServiceStreamingResponseHandler}.
 * <p>
 * Without the fix the tool result is sent back to the LLM for another round trip, so a tool
 * meant to close the conversation (for instance one that clears the chat memory) silently causes
 * an extra model call.
 */
public class StreamingImmediateReturnToolTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyAiService.class, MyTools.class, MyStreamingChatModel.class,
                            MyStreamingChatModelSupplier.class, MyMemoryProviderSupplier.class));

    @Inject
    MyAiService aiService;

    @Test
    @ActivateRequestContext
    void immediateReturnToolDoesNotTriggerAnotherModelCall() {
        MyStreamingChatModel.callCount.set(0);

        List<String> tokens = aiService.chat("call-calculator")
                .collect().asList()
                .await().atMost(Duration.ofSeconds(10));

        // the tool result is not sent back to the LLM, so no text is generated after the tool call
        assertThat(tokens).isEmpty();
        assertThat(MyStreamingChatModel.callCount).hasValue(1);
    }

    @Test
    @ActivateRequestContext
    void regularToolStillTriggersAnotherModelCall() {
        MyStreamingChatModel.callCount.set(0);

        List<String> tokens = aiService.chat("call-processor")
                .collect().asList()
                .await().atMost(Duration.ofSeconds(10));

        assertThat(String.join("", tokens)).isEqualTo("The processor returned: 42");
        assertThat(MyStreamingChatModel.callCount).hasValue(2);
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = MyStreamingChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface MyAiService {

        @ToolBox(MyTools.class)
        Multi<String> chat(@UserMessage String message);
    }

    @Singleton
    public static class MyTools {

        @Tool(returnBehavior = ReturnBehavior.IMMEDIATE)
        public String calculator() {
            return "42";
        }

        @Tool
        public String processor() {
            return "42";
        }
    }

    public static class MyStreamingChatModelSupplier implements Supplier<StreamingChatModel> {
        @Override
        public StreamingChatModel get() {
            return new MyStreamingChatModel();
        }
    }

    public static class MyStreamingChatModel implements StreamingChatModel {

        static final AtomicInteger callCount = new AtomicInteger();

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            callCount.incrementAndGet();
            List<ChatMessage> messages = chatRequest.messages();

            if (messages.size() == 1) {
                String userMessage = ((dev.langchain4j.data.message.UserMessage) messages.get(0)).singleText();
                String toolName = userMessage.equals("call-calculator") ? "calculator" : "processor";
                handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(new AiMessage("", List.of(ToolExecutionRequest.builder()
                                .id("my-tool-" + toolName)
                                .name(toolName)
                                .arguments("{}")
                                .build())))
                        .tokenUsage(new TokenUsage(0, 0))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build());
                return;
            }

            if (messages.size() == 3) {
                ToolExecutionResultMessage toolResult = (ToolExecutionResultMessage) messages.get(2);
                handler.onPartialResponse("The processor returned: ");
                handler.onPartialResponse(toolResult.text());
                handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(new AiMessage("The processor returned: " + toolResult.text()))
                        .tokenUsage(new TokenUsage(0, 0))
                        .finishReason(FinishReason.STOP)
                        .build());
                return;
            }

            handler.onError(new RuntimeException("Unexpected number of messages: " + messages.size()));
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
