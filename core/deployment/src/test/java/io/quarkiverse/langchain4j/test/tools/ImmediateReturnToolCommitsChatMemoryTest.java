package io.quarkiverse.langchain4j.test.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verifies that committableChatMemory.commit() is called on the immediateToolReturn
 * path in AiServiceMethodImplementationSupport.doImplement0.
 *
 * <pre>
 * The immediateToolReturn path is a success path - the tool deliberately chose to exit
 * the loop via ReturnBehavior.IMMEDIATE. All messages added during the tool loop
 * (AI messages, tool results) must be persisted to the ChatMemoryStore.
 * </pre>
 */
public class ImmediateReturnToolCommitsChatMemoryTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyAiService.class, MyTool.class,
                            TrackingChatMemoryStore.class,
                            TrackingChatMemoryStoreSupplier.class,
                            FakeChatModelSupplier.class, FakeChatModel.class));

    @Inject
    MyAiService aiService;

    @BeforeEach
    void reset() {
        TrackingChatMemoryStore.INSTANCE.clear();
    }

    @Test
    @ActivateRequestContext
    void immediateToolReturnShouldCommitChatMemory() {
        String memoryId = "test-memory";
        Result<String> result = aiService.chat(memoryId, "call-immediate");

        // Verify the tool returned immediately
        assertNull(result.content());
        assertThat(result.finishReason()).isEqualTo(FinishReason.TOOL_EXECUTION);
        assertThat(result.toolExecutions()).hasSize(1);
        assertThat(result.toolExecutions().get(0).result()).isEqualTo("\"immediate-result\"");

        // The key assertion: the chat memory store must have received an update,
        // meaning committableChatMemory.commit() was called
        assertThat(TrackingChatMemoryStore.INSTANCE.updateCount(memoryId))
                .as("ChatMemoryStore.updateMessages should have been called at least once "
                        + "for the immediateToolReturn path")
                .isGreaterThanOrEqualTo(1);

        // Verify the stored messages contain the full conversation
        List<ChatMessage> stored = TrackingChatMemoryStore.INSTANCE.getMessages(memoryId);
        assertThat(stored).isNotEmpty();
        // Should contain: user message, AI message (tool request), tool result
        assertThat(stored).hasSize(3);
    }

    @RegisterAiService(chatLanguageModelSupplier = FakeChatModelSupplier.class, chatMemoryProviderSupplier = TrackingChatMemoryStoreSupplier.class, tools = MyTool.class)
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
     * <pre>
     * A ChatMemoryStore that tracks how many times updateMessages is called per memoryId.
     * This lets us assert that commit() was invoked on the committableChatMemory.
     * </pre>
     */
    public static class TrackingChatMemoryStore implements ChatMemoryStore {

        static final TrackingChatMemoryStore INSTANCE = new TrackingChatMemoryStore();

        private final Map<Object, List<ChatMessage>> store = new ConcurrentHashMap<>();
        private final Map<Object, Integer> updateCounts = new ConcurrentHashMap<>();

        @Override
        public List<ChatMessage> getMessages(Object memoryId) {
            return new ArrayList<>(store.getOrDefault(memoryId, List.of()));
        }

        @Override
        public void updateMessages(Object memoryId, List<ChatMessage> messages) {
            store.put(memoryId, new ArrayList<>(messages));
            updateCounts.merge(memoryId, 1, Integer::sum);
        }

        @Override
        public void deleteMessages(Object memoryId) {
            store.remove(memoryId);
            updateCounts.remove(memoryId);
        }

        public int updateCount(Object memoryId) {
            return updateCounts.getOrDefault(memoryId, 0);
        }

        public void clear() {
            store.clear();
            updateCounts.clear();
        }
    }

    public static class TrackingChatMemoryStoreSupplier implements Supplier<ChatMemoryProvider> {
        @Override
        public ChatMemoryProvider get() {
            return memoryId -> MessageWindowChatMemory.builder()
                    .id(memoryId)
                    .maxMessages(20)
                    .chatMemoryStore(TrackingChatMemoryStore.INSTANCE)
                    .build();
        }
    }

    public static class FakeChatModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new FakeChatModel();
        }
    }

    /**
     * <pre>
     * A fake ChatModel that always responds with a tool execution request
     * for the "immediateAction" tool, extracting the tool name from the user message.
     * </pre>
     */
    public static class FakeChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            List<ChatMessage> messages = chatRequest.messages();
            // First call: respond with a tool execution request
            if (messages.stream().noneMatch(m -> m instanceof dev.langchain4j.data.message.ToolExecutionResultMessage)) {
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
            // Should not reach here for IMMEDIATE tools
            throw new RuntimeException(
                    "FakeChatModel should not be called a second time for IMMEDIATE return tools");
        }
    }
}
