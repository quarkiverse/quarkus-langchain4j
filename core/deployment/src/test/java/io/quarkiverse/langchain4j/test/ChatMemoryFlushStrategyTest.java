package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatMemoryFlushStrategy;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verifies that a custom {@code ChatMemoryFlushStrategy} configured via
 * {@code chatMemoryFlushStrategySupplier} on {@code @RegisterAiService} is used.
 *
 * <pre>
 * This test uses a ChatModel that triggers a tool call that always throws,
 * which causes the tool loop to fail. With the default DEFERRED strategy,
 * commit() is not called on failure. With IMMEDIATE, the messages are
 * persisted to the ChatMemoryStore as they are added.
 * </pre>
 */
public class ChatMemoryFlushStrategyTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyAiService.class, FailingTool.class,
                            TrackingChatMemoryStore.class,
                            TrackingChatMemoryStoreSupplier.class,
                            ImmediateFlushStrategySupplier.class,
                            FakeChatModelSupplier.class, FakeChatModel.class));

    @Inject
    MyAiService aiService;

    @BeforeEach
    void reset() {
        TrackingChatMemoryStore.INSTANCE.clear();
    }

    @Test
    @ActivateRequestContext
    void immediateFlushStrategyShouldPersistOnFailure() {
        String memoryId = "fail-test";

        assertThatThrownBy(() -> aiService.chat(memoryId, "call the tool"))
                .isInstanceOf(RuntimeException.class);

        // With IMMEDIATE flush strategy, even on failure, the memory should be persisted
        assertThat(TrackingChatMemoryStore.INSTANCE.updateCount(memoryId))
                .as("ChatMemoryStore.updateMessages should have been called "
                        + "even on failure when using IMMEDIATE flush strategy")
                .isGreaterThanOrEqualTo(1);

        // The store should have at least the user message
        List<ChatMessage> stored = TrackingChatMemoryStore.INSTANCE.getMessages(memoryId);
        assertThat(stored).isNotEmpty();
    }

    @RegisterAiService(chatLanguageModelSupplier = FakeChatModelSupplier.class, chatMemoryProviderSupplier = TrackingChatMemoryStoreSupplier.class, chatMemoryFlushStrategySupplier = ImmediateFlushStrategySupplier.class, tools = FailingTool.class)
    public interface MyAiService {
        String chat(@MemoryId String memoryId, @UserMessage String message);
    }

    @ApplicationScoped
    public static class FailingTool {
        @Tool("A tool that always fails")
        public String failingAction(String input) {
            throw new RuntimeException("Tool execution failed on purpose");
        }
    }

    @ApplicationScoped
    public static class PingService {
        public String ping() {
            return "pong";
        }
    }

    @ApplicationScoped
    public static class ImmediateFlushStrategySupplier implements Supplier<ChatMemoryFlushStrategy> {

        public ImmediateFlushStrategySupplier(PingService pingService) {
            System.out.println("ping: " + pingService.ping());
        }

        @Override
        public ChatMemoryFlushStrategy get() {
            return ChatMemoryFlushStrategy.IMMEDIATE;
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
     * for the "failingAction" tool, which will throw an exception.
     * </pre>
     */
    public static class FakeChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return ChatResponse.builder()
                    .aiMessage(new AiMessage("", List.of(
                            ToolExecutionRequest.builder()
                                    .id("tool-1")
                                    .name("failingAction")
                                    .arguments("{\"input\":\"test\"}")
                                    .build())))
                    .finishReason(FinishReason.TOOL_EXECUTION)
                    .build();
        }
    }
}
