package io.quarkiverse.langchain4j.test.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

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
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * A tool declaring {@link ReturnBehavior#IMMEDIATE} ends the tool-calling loop and the AI service returns the tool's
 * result. When the AI service method returns {@code Result<T>}, the blocking path commits the chat memory and fires
 * {@link AiServiceCompletedEvent} before returning (see {@link ImmediateReturnToolCommitsChatMemoryTest}). A method
 * returning the tool's type directly must do the same: otherwise, with the default deferred flush strategy, the user
 * message, the AI message carrying the tool request and the tool result never reach the {@link ChatMemoryStore}, and
 * no completion event is fired.
 */
public class ImmediateReturnDirectTypeCommitsChatMemoryTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(DirectReturnAiService.class, MyTool.class,
                            TrackingChatMemoryStore.class, TrackingChatMemoryStoreSupplier.class,
                            FakeChatModelSupplier.class, FakeChatModel.class, CompletedEventObserver.class));

    @Inject
    DirectReturnAiService directReturnAiService;

    @Inject
    CompletedEventObserver completedEventObserver;

    @BeforeEach
    void reset() {
        TrackingChatMemoryStore.INSTANCE.clear();
        completedEventObserver.events.clear();
    }

    @Test
    @ActivateRequestContext
    void directReturnShouldCommitChatMemory() {
        String memoryId = "direct-memory";
        String result = directReturnAiService.chat(memoryId, "call-immediate");

        assertThat(result).isEqualTo("immediate-result");
        assertStoredConversation(memoryId);
        assertThat(completedEventObserver.events).hasSize(1);
        assertThat(completedEventObserver.events.get(0).result()).contains("immediate-result");
    }

    private static void assertStoredConversation(String memoryId) {
        assertThat(TrackingChatMemoryStore.INSTANCE.updateCount(memoryId))
                .as("ChatMemoryStore.updateMessages should have been called for the immediate-return path")
                .isGreaterThanOrEqualTo(1);

        List<ChatMessage> stored = TrackingChatMemoryStore.INSTANCE.getMessages(memoryId);
        assertThat(stored).hasSize(3);
        assertThat(stored.get(0)).isInstanceOf(UserMessage.class);
        assertThat(stored.get(1)).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) stored.get(1)).hasToolExecutionRequests()).isTrue();
        assertThat(stored.get(2)).isInstanceOf(ToolExecutionResultMessage.class);
        assertThat(((ToolExecutionResultMessage) stored.get(2)).text()).isEqualTo("immediate-result");
    }

    @RegisterAiService(chatLanguageModelSupplier = FakeChatModelSupplier.class, chatMemoryProviderSupplier = TrackingChatMemoryStoreSupplier.class, tools = MyTool.class)
    public interface DirectReturnAiService {
        String chat(@MemoryId String memoryId, @dev.langchain4j.service.UserMessage String message);
    }

    @ApplicationScoped
    public static class MyTool {
        @Tool(returnBehavior = ReturnBehavior.IMMEDIATE)
        public String immediateAction(String input) {
            return "immediate-result";
        }
    }

    @Singleton
    public static class CompletedEventObserver {
        final List<AiServiceCompletedEvent> events = new CopyOnWriteArrayList<>();

        void onCompleted(@Observes AiServiceCompletedEvent event) {
            events.add(event);
        }
    }

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
     * Always answers the first request with a tool execution request for {@code immediateAction}. A second request
     * would mean the tool result was sent back to the LLM, which {@link ReturnBehavior#IMMEDIATE} forbids.
     */
    public static class FakeChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            List<ChatMessage> messages = chatRequest.messages();
            if (messages.size() == 1) {
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from(ToolExecutionRequest.builder()
                                .id("call-1")
                                .name("immediateAction")
                                .arguments("{\"input\":\"x\"}")
                                .build()))
                        .build();
            }
            throw new IllegalStateException(
                    "The tool declared ReturnBehavior.IMMEDIATE, so its result must not be sent back to the model");
        }
    }
}
