package io.quarkiverse.langchain4j.test.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.event.Observes;
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
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.test.Lists;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * A {@link ToolExecutionResult} may carry any combination of {@link dev.langchain4j.data.message.Content}
 * elements - typically a single {@link TextContent}, but some providers also accept {@link ImageContent} in
 * tool results. The whole result must therefore be carried over into the
 * {@link ToolExecutionResultMessage} that is appended to the chat memory and sent back to the LLM, exactly
 * like upstream {@code ToolService#toResultMessage} does.
 */
public class ToolResultWithNonTextContentTest {

    static final String IMAGE_URL = "https://example.com/chart.png";

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyAiService.class, MyTools.class, EventCollector.class,
                            MyChatModelSupplier.class, MyChatModel.class, Lists.class));

    @Inject
    MyAiService aiService;

    @Inject
    EventCollector eventCollector;

    @Test
    @ActivateRequestContext
    void toolResultContentsReachTheLlmAndTheChatMemory() {
        Result<String> r = aiService.chat("hello");

        assertThat(r.content()).isEqualTo("response: text=here is the chart image=" + IMAGE_URL);

        ToolExecutionResultMessage toolResult = MyChatModel.lastToolResult;
        assertThat(toolResult).isNotNull();
        assertThat(toolResult.id()).isEqualTo("tool-1");
        assertThat(toolResult.toolName()).isEqualTo("chart");
        assertThat(toolResult.contents()).containsExactly(
                TextContent.from("here is the chart"),
                ImageContent.from(IMAGE_URL));
    }

    @Test
    @ActivateRequestContext
    void toolExecutedEventCarriesTheResultContents() {
        eventCollector.toolExecutedEvents.clear();

        aiService.chat("hello");

        assertThat(eventCollector.toolExecutedEvents).hasSize(1);
        assertThat(eventCollector.toolExecutedEvents.get(0).resultContents()).containsExactly(
                TextContent.from("here is the chart"),
                ImageContent.from(IMAGE_URL));
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, tools = MyTools.class)
    public interface MyAiService {
        Result<String> chat(@UserMessage String message);
    }

    @ApplicationScoped
    public static class MyTools {

        @Tool
        public ToolExecutionResult chart() {
            return ToolExecutionResult.builder()
                    .resultContents(List.of(
                            TextContent.from("here is the chart"),
                            ImageContent.from(IMAGE_URL)))
                    .build();
        }
    }

    // no client proxy, so that the field can be read directly
    @Singleton
    public static class EventCollector {
        final List<ToolExecutedEvent> toolExecutedEvents = new ArrayList<>();

        void onToolExecuted(@Observes ToolExecutedEvent event) {
            toolExecutedEvents.add(event);
        }
    }

    public static class MyChatModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new MyChatModel();
        }
    }

    public static class MyChatModel implements ChatModel {

        static volatile ToolExecutionResultMessage lastToolResult;

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            List<ChatMessage> messages = chatRequest.messages();
            if (messages.size() == 1) {
                return ChatResponse.builder()
                        .aiMessage(new AiMessage("", List.of(
                                ToolExecutionRequest.builder()
                                        .id("tool-1")
                                        .name("chart")
                                        .arguments("{}")
                                        .build())))
                        .tokenUsage(new TokenUsage(0, 0))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build();
            } else if (messages.size() == 3) {
                ToolExecutionResultMessage toolResult = (ToolExecutionResultMessage) Lists.last(messages);
                lastToolResult = toolResult;
                StringBuilder sb = new StringBuilder("response:");
                for (var content : toolResult.contents()) {
                    if (content instanceof TextContent textContent) {
                        sb.append(" text=").append(textContent.text());
                    } else if (content instanceof ImageContent imageContent) {
                        sb.append(" image=").append(imageContent.image().url());
                    }
                }
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from(sb.toString()))
                        .finishReason(FinishReason.STOP)
                        .build();
            }
            throw new RuntimeException("Unexpected number of messages: " + messages.size());
        }
    }
}
