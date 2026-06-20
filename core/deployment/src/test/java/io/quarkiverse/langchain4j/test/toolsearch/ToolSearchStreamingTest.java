package io.quarkiverse.langchain4j.test.toolsearch;

import static dev.langchain4j.data.message.ChatMessageType.TOOL_EXECUTION_RESULT;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

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
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.tool.search.ToolSearchStrategy;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;

/**
 * The streaming path must honor the tool search strategy the same way the blocking path does.
 */
public class ToolSearchStreamingTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(FakeToolSearchStrategy.class,
                            BookingTools.class,
                            StreamingServiceWithToolSearch.class));

    @RegisterAiService(tools = BookingTools.class, toolSearchStrategy = ToolSearchStrategy.class)
    interface StreamingServiceWithToolSearch {

        Multi<String> chat(@UserMessage String msg, @MemoryId Object id);

    }

    @Inject
    StreamingServiceWithToolSearch service;

    @Test
    @ActivateRequestContext
    void searchedToolIsExecuted() {
        List<String> tokens = service.chat("get my booking details", 1)
                .collect().asList()
                .await().atMost(Duration.ofSeconds(30));
        assertEquals("REAL_TOOL_RESULT", String.join("", tokens));
    }

    /**
     * Streaming counterpart of {@code ToolSearchModel}: same staged expectations, driven through a
     * {@link StreamingChatResponseHandler}.
     */
    @ApplicationScoped
    public static class StreamingModel implements StreamingChatModel {

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            List<ChatMessage> messages = chatRequest.messages();
            ChatMessage lastMessage = messages.get(messages.size() - 1);

            if (lastMessage.type().equals(TOOL_EXECUTION_RESULT)) {
                ToolExecutionResultMessage result = (ToolExecutionResultMessage) lastMessage;
                if (result.text().contains("REAL_TOOL_RESULT")) {
                    answer(handler, "REAL_TOOL_RESULT");
                } else if (hasTool(chatRequest, FakeToolSearchStrategy.FOUND_TOOL_NAME)) {
                    callTool(handler, FakeToolSearchStrategy.FOUND_TOOL_NAME);
                } else {
                    answer(handler, "FOUND_TOOL_NOT_AVAILABLE");
                }
                return;
            }

            if (hasTool(chatRequest, FakeToolSearchStrategy.FOUND_TOOL_NAME)) {
                answer(handler, "TOOL_NOT_NARROWED");
            } else if (hasTool(chatRequest, FakeToolSearchStrategy.SEARCH_TOOL_NAME)) {
                callTool(handler, FakeToolSearchStrategy.SEARCH_TOOL_NAME);
            } else {
                answer(handler, "NO_SEARCH_TOOL");
            }
        }

        private static boolean hasTool(ChatRequest chatRequest, String name) {
            List<ToolSpecification> toolSpecifications = chatRequest.toolSpecifications();
            if (toolSpecifications == null) {
                return false;
            }
            return toolSpecifications.stream().anyMatch(spec -> spec.name().equals(name));
        }

        private static void callTool(StreamingChatResponseHandler handler, String name) {
            ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder().name(name).id(name).build();
            handler.onCompleteResponse(ChatResponse.builder()
                    .aiMessage(AiMessage.from(toolExecutionRequest))
                    .finishReason(FinishReason.TOOL_EXECUTION)
                    .build());
        }

        private static void answer(StreamingChatResponseHandler handler, String text) {
            handler.onPartialResponse(text);
            handler.onCompleteResponse(ChatResponse.builder()
                    .aiMessage(new AiMessage(text))
                    .finishReason(FinishReason.STOP)
                    .build());
        }
    }
}
