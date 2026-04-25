package io.quarkiverse.langchain4j.test.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.runtime.aiservice.NoopChatMemory;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Regression test for issue #2344: NoopChatMemory fails with
 * "messages cannot be null or empty" on second chat request.
 *
 * <pre>
 * Scenario: Tool execution with NoopChatMemory (stateless agents, no memory provider)
 * fails during the second chat request with IllegalArgumentException because
 * ChatRequest constructor requires non-empty messages, but when NoopChatMemory's
 * message list is empty (tool execution consumed messages but didn't refill),
 * the second request crashes.
 *
 * This test verifies that the fix handles the edge case where NoopChatMemory returns
 * an empty message list during a tool call's second request by injecting the userMessage
 * as a fallback to prevent the crash.
 * </pre>
 */
public class NoopChatMemorySecondRequestTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyAiService.class, MyTool.class, FakeChatModelWithSecondCall.class));

    @Inject
    MyAiService aiService;

    @Test
    @ActivateRequestContext
    void secondRequestWithNoopChatMemoryAndImmediateReturnToolShouldNotCrash() {
        // First request - triggers tool execution and should succeed
        assertThatCode(() -> {
            Result<String> firstResult = aiService.chat("first-request");
            assertThat(firstResult.finishReason()).isEqualTo(FinishReason.TOOL_EXECUTION);
            assertThat(firstResult.toolExecutions()).hasSize(1);
        }).doesNotThrowAnyException();

        // Second request - this is where the bug manifested:
        // with NoopChatMemory, messages were consumed by first tool execution and
        // the second request's ChatRequest would fail with
        // "messages cannot be null or empty"
        assertThatCode(() -> {
            Result<String> secondResult = aiService.chat("second-request");
            // The fix should allow this to proceed without IllegalArgumentException
            // The exact outcome depends on the FakeChatModel behavior
            assertThat(secondResult).isNotNull();
        }).doesNotThrowAnyException();

        // Third request to further verify stability
        assertThatCode(() -> {
            Result<String> thirdResult = aiService.chat("third-request");
            assertThat(thirdResult).isNotNull();
        }).doesNotThrowAnyException();
    }

    @RegisterAiService(chatLanguageModelSupplier = FakeChatModelWithSecondCallSupplier.class, tools = MyTool.class)
    public interface MyAiService {
        Result<String> chat(@MemoryId String memoryId, @UserMessage String message);
    }

    @ApplicationScoped
    public static class MyTool {
        @Tool(returnBehavior = ReturnBehavior.IMMEDIATE)
        public String immediateAction(String input) {
            return "immediate-result";
        }
    }

    /**
     * FakeChatModel that handles multiple calls properly.
     * First call: responds with tool execution request
     * Second call: simulates what happens when NoopChatMemory has empty messages
     * (with the fix, this should not happen - userMessage fallback is used instead)
     */
    public static class FakeChatModelWithSecondCall implements ChatModel {
        private int callCount = 0;

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            callCount++;
            List<ChatMessage> messages = chatRequest.messages();

            // First call: respond with tool execution request
            if (callCount == 1) {
                assertThat(messages).isNotEmpty(); // Should have at least the user message
                return ChatResponse.builder()
                        .aiMessage(new AiMessage("", List.of(
                                ToolExecutionRequest.builder()
                                        .id("tool-1")
                                        .name("immediateAction")
                                        .arguments("{\"input\":\"test\"}")
                                        .build())))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build();
            }

            // Subsequent calls: With the fix, messages should not be empty
            // (userMessage fallback is injected when NoopChatMemory returns empty list)
            assertThat(messages)
                    .as("ChatRequest messages should never be empty - fix should inject userMessage fallback")
                    .isNotEmpty();

            // Return a simple response for subsequent calls
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("Response for call " + callCount))
                    .finishReason(FinishReason.SINGLE)
                    .build();
        }
    }

    public static class FakeChatModelWithSecondCallSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new FakeChatModelWithSecondCall();
        }
    }
}