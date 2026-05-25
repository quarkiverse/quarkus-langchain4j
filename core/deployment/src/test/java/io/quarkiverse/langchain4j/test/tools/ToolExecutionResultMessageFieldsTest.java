package io.quarkiverse.langchain4j.test.tools;

import static org.assertj.core.api.Assertions.assertThat;

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
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

public class ToolExecutionResultMessageFieldsTest {

    private static final Map<String, Object> TOOL_RESULT_ATTRIBUTES = Map.of(
            "statusCode", 422,
            "reason", "invalid-city");

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyAiService.class, MyTools.class,
                            TrackingChatMemoryStore.class,
                            TrackingChatMemoryStoreSupplier.class,
                            MyChatModelSupplier.class, MyChatModel.class));

    @Inject
    MyAiService aiService;

    @BeforeEach
    void reset() {
        TrackingChatMemoryStore.INSTANCE.clear();
    }

    @Test
    @ActivateRequestContext
    void toolExecutionResultMessagePersistsIsErrorAndAttributes() {
        String memoryId = "tool-result-fields";

        String result = aiService.chat(memoryId, "call the validation tool");

        assertThat(result).isEqualTo("response: Tool execution failed");
        List<ChatMessage> stored = TrackingChatMemoryStore.INSTANCE.getMessages(memoryId);
        ToolExecutionResultMessage toolResult = stored.stream()
                .filter(ToolExecutionResultMessage.class::isInstance)
                .map(ToolExecutionResultMessage.class::cast)
                .findFirst()
                .orElseThrow();

        assertThat(toolResult.id()).isEqualTo("tool-validateCity");
        assertThat(toolResult.toolName()).isEqualTo("validateCity");
        assertThat(toolResult.text()).isEqualTo("Tool execution failed");
        assertThat(toolResult.isError()).isTrue();
        assertThat(toolResult.attributes()).isEqualTo(TOOL_RESULT_ATTRIBUTES);
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = TrackingChatMemoryStoreSupplier.class, tools = MyTools.class)
    public interface MyAiService {
        String chat(@MemoryId String memoryId, @UserMessage String message);
    }

    @ApplicationScoped
    public static class MyTools {
        @Tool
        public ToolExecutionResult validateCity(String city) {
            return ToolExecutionResult.builder()
                    .resultText("Tool execution failed")
                    .isError(true)
                    .attributes(TOOL_RESULT_ATTRIBUTES)
                    .build();
        }
    }

    public static class TrackingChatMemoryStore implements ChatMemoryStore {

        static final TrackingChatMemoryStore INSTANCE = new TrackingChatMemoryStore();

        private final Map<Object, List<ChatMessage>> store = new ConcurrentHashMap<>();

        @Override
        public List<ChatMessage> getMessages(Object memoryId) {
            return new ArrayList<>(store.getOrDefault(memoryId, List.of()));
        }

        @Override
        public void updateMessages(Object memoryId, List<ChatMessage> messages) {
            store.put(memoryId, new ArrayList<>(messages));
        }

        @Override
        public void deleteMessages(Object memoryId) {
            store.remove(memoryId);
        }

        public void clear() {
            store.clear();
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
                        .aiMessage(new AiMessage("", List.of(
                                ToolExecutionRequest.builder()
                                        .id("tool-validateCity")
                                        .name("validateCity")
                                        .arguments("{\"city\":\"Atlantis\"}")
                                        .build())))
                        .tokenUsage(new TokenUsage(0, 0))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build();
            } else if (messages.size() == 3) {
                ToolExecutionResultMessage toolResult = (ToolExecutionResultMessage) messages.get(2);
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from("response: " + toolResult.text()))
                        .finishReason(FinishReason.STOP)
                        .build();
            }
            throw new RuntimeException("Unexpected number of messages: " + messages.size());
        }
    }
}
