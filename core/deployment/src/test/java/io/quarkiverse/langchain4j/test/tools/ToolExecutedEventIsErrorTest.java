package io.quarkiverse.langchain4j.test.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import jakarta.enterprise.context.control.ActivateRequestContext;
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
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;

/**
 * LangChain4j 1.23.0 added {@link ToolExecutedEvent#isError()}, and its default implementation throws
 * an {@link UnsupportedOperationException} when the integration that created the event did not report
 * the value. Upstream {@code ToolService} always reports it, so an observer of this documented
 * {@code @Observes} feature must be able to read it in Quarkus too - on the blocking path as well as
 * on the streaming one, both of which build the event themselves instead of going through upstream.
 * <p>
 * The information is available at both call sites: the very next statement there already consults
 * {@code ToolExecutionResult#isError()} to decide whether to send the error back to the LLM.
 */
public class ToolExecutedEventIsErrorTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(BlockingAiService.class, StreamingAiService.class, MyTools.class,
                            EventCollector.class, MyChatModelSupplier.class, MyChatModel.class,
                            MyStreamingChatModelSupplier.class, MyStreamingChatModel.class,
                            MyMemoryProviderSupplier.class));

    @Inject
    BlockingAiService blockingAiService;

    @Inject
    StreamingAiService streamingAiService;

    @Inject
    EventCollector eventCollector;

    @BeforeEach
    void setUp() {
        eventCollector.events.clear();
    }

    @Test
    @ActivateRequestContext
    void successfulToolIsNotReportedAsAnError() {
        assertThat(blockingAiService.chat("ok")).isEqualTo("the tool said: all good");

        assertThat(eventCollector.events).hasSize(1);
        assertThat(eventCollector.events.get(0).isError()).isFalse();
    }

    @Test
    @ActivateRequestContext
    void failedToolIsReportedAsAnError() {
        blockingAiService.chat("boom");

        assertThat(eventCollector.events).hasSize(1);
        assertThat(eventCollector.events.get(0).isError()).isTrue();
    }

    @Test
    @ActivateRequestContext
    void successfulToolIsNotReportedAsAnErrorWhenStreaming() {
        String answer = String.join("", streamingAiService.chat("ok")
                .collect().asList()
                .await().atMost(Duration.ofSeconds(10)));

        assertThat(answer).isEqualTo("the tool said: all good");

        assertThat(eventCollector.events).hasSize(1);
        assertThat(eventCollector.events.get(0).isError()).isFalse();
    }

    @Test
    @ActivateRequestContext
    void failedToolIsReportedAsAnErrorWhenStreaming() {
        streamingAiService.chat("boom")
                .collect().asList()
                .await().atMost(Duration.ofSeconds(10));

        assertThat(eventCollector.events).hasSize(1);
        assertThat(eventCollector.events.get(0).isError()).isTrue();
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class)
    public interface BlockingAiService {

        @ToolBox(MyTools.class)
        String chat(@UserMessage String message);
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = MyStreamingChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface StreamingAiService {

        @ToolBox(MyTools.class)
        Multi<String> chat(@UserMessage String message);
    }

    @Singleton
    public static class MyTools {

        @Tool
        public String ok() {
            return "all good";
        }

        @Tool
        public String boom() {
            throw new IllegalStateException("the tool blew up");
        }
    }

    // no client proxy, so that the field can be read directly
    @Singleton
    public static class EventCollector {
        final List<ToolExecutedEvent> events = new CopyOnWriteArrayList<>();

        void onToolExecuted(@Observes ToolExecutedEvent event) {
            events.add(event);
        }
    }

    private static String getRequestedToolName(List<ChatMessage> messages) {
        // gets the name of the tool that the client wants to execute - for purposes of this test, this is
        // passed to the chat model as the text content in the last user message
        return ((dev.langchain4j.data.message.UserMessage) messages.get(0)).singleText();
    }

    private static AiMessage toolCall(String toolName) {
        return new AiMessage("", List.of(ToolExecutionRequest.builder()
                .id("tool-" + toolName)
                .name(toolName)
                .arguments("{}")
                .build()));
    }

    public static class MyChatModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new MyChatModel();
        }
    }

    public static class MyChatModel implements ChatModel {

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            List<ChatMessage> messages = chatRequest.messages();
            if (messages.size() == 1) {
                return ChatResponse.builder()
                        .aiMessage(toolCall(getRequestedToolName(messages)))
                        .tokenUsage(new TokenUsage(0, 0))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build();
            }
            if (messages.size() == 3) {
                ToolExecutionResultMessage toolResult = (ToolExecutionResultMessage) messages.get(2);
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from("the tool said: " + toolResult.text()))
                        .tokenUsage(new TokenUsage(0, 0))
                        .finishReason(FinishReason.STOP)
                        .build();
            }
            throw new RuntimeException("Unexpected number of messages: " + messages.size());
        }
    }

    public static class MyStreamingChatModelSupplier implements Supplier<StreamingChatModel> {
        @Override
        public StreamingChatModel get() {
            return new MyStreamingChatModel();
        }
    }

    public static class MyStreamingChatModel implements StreamingChatModel {

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            List<ChatMessage> messages = chatRequest.messages();
            if (messages.size() == 1) {
                handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(toolCall(getRequestedToolName(messages)))
                        .tokenUsage(new TokenUsage(0, 0))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build());
                return;
            }
            if (messages.size() == 3) {
                ToolExecutionResultMessage toolResult = (ToolExecutionResultMessage) messages.get(2);
                String answer = "the tool said: " + toolResult.text();
                handler.onPartialResponse(answer);
                handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(AiMessage.from(answer))
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
