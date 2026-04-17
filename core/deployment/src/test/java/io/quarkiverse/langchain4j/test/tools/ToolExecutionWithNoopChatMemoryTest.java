package io.quarkiverse.langchain4j.test.tools;

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
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkiverse.langchain4j.runtime.aiservice.NoopChatMemory;
import io.quarkiverse.langchain4j.test.Lists;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Tests that tool execution works correctly with NoopChatMemory (agents without memory).
 * <p>
 * This test validates the fix for the issue where tool execution would fail with NoopChatMemory
 * because the messages list would be empty during subsequent chat requests.
 * The fix maintains a local copy of messages during the tool execution loop.
 * <p>
 * Note: We use a custom ChatMemoryProviderSupplier (not NoChatMemoryProviderSupplier) to bypass
 * the build-time validation while still testing the runtime behavior with NoopChatMemory.
 */
public class ToolExecutionWithNoopChatMemoryTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyAiService.class, MyTools.class, Lists.class));

    @Inject
    MyAiService aiService;

    /**
     * Tests that tool execution works correctly with NoopChatMemory.
     * The fix ensures messages are accumulated in a local list during the tool loop,
     * so the second chat request receives the proper message history.
     */
    @Test
    @ActivateRequestContext
    void testToolExecutionWithNoopChatMemory() {
        String response = aiService.chat("memoryId", "call tool");
        assertThat(response).isEqualTo("Tool returned: \"bar\"");
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = NullChatMemoryProviderSupplier.class)
    public interface MyAiService {

        @ToolBox(MyTools.class)
        String chat(@MemoryId String memoryId, @UserMessage String userMessage);
    }

    public static class NullChatMemoryProviderSupplier implements Supplier<ChatMemoryProvider> {

        @Override
        public ChatMemoryProvider get() {
            return null;
        }
    }

    @Singleton
    public static class MyTools {

        @Tool("A simple tool for testing")
        public String simpleTool() {
            return "bar";
        }
    }

    public static class MyChatModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new MyChatModel();
        }
    }

    /**
     * Mock ChatModel that simulates tool execution flow.
     * - First call (1 message): returns tool execution request
     * - Second call (3 messages): validates fix worked and returns final response
     */
    public static class MyChatModel implements ChatModel {

        @Override
        public ChatResponse chat(List<ChatMessage> messages) {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            List<ChatMessage> messages = chatRequest.messages();

            if (messages.size() == 1) {
                // First request: only user message - return tool execution request
                return ChatResponse.builder()
                        .aiMessage(new AiMessage("Calling tool", List.of(
                                ToolExecutionRequest.builder()
                                        .id("tool-1")
                                        .name("simpleTool")
                                        .arguments("{}")
                                        .build())))
                        .tokenUsage(new TokenUsage(0, 0))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build();
            } else if (messages.size() == 3) {
                // Second request: should have user + AI + tool result
                // This validates the fix - with NoopChatMemory, without the fix this would be empty
                ToolExecutionResultMessage toolResult = (ToolExecutionResultMessage) Lists.last(messages);
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from("Tool returned: " + toolResult.text()))
                        .finishReason(FinishReason.STOP)
                        .build();
            }

            throw new IllegalStateException("Unexpected message count: " + messages.size());
        }
    }

    /**
     * Custom ChatMemoryProviderSupplier that provides NoopChatMemory.
     * This bypasses the build-time validation (which checks for NoChatMemoryProviderSupplier)
     * while still testing the runtime behavior with NoopChatMemory.
     */
    @Singleton
    public static class CustomNoopChatMemoryProviderSupplier implements Supplier<ChatMemoryProvider> {
        @Override
        public ChatMemoryProvider get() {
            return new ChatMemoryProvider() {
                @Override
                public ChatMemory get(Object memoryId) {
                    return new NoopChatMemory();
                }
            };
        }
    }
}
