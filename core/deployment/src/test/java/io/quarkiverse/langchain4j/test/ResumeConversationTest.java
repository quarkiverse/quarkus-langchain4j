package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

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
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ResumeConversation;
import io.quarkus.test.QuarkusUnitTest;

class ResumeConversationTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Assistant.class, TestChatMemoryStore.class, TestChatMemoryProviderSupplier.class,
                            TestChatModel.class, TestChatModelSupplier.class));

    @Inject
    Assistant assistant;

    @BeforeEach
    void reset() {
        TestChatMemoryStore.MESSAGES.clear();
        TestChatModel.lastRequest = null;
    }

    @Test
    @ActivateRequestContext
    void resumesFromToolResultWithoutAddingUserMessage() {
        String memoryId = "conversation";
        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                .id("approval-1")
                .name("requestApproval")
                .arguments("{}")
                .build();
        List<ChatMessage> suspendedConversation = List.of(
                UserMessage.from("Delete the deployment"),
                AiMessage.from(toolRequest),
                ToolExecutionResultMessage.from(toolRequest, "approved"));
        TestChatMemoryStore.MESSAGES.put(memoryId, new ArrayList<>(suspendedConversation));

        assertThat(assistant.resume(memoryId)).isEqualTo("Conversation continued");

        assertThat(TestChatModel.lastRequest.messages()).containsExactlyElementsOf(suspendedConversation);
        assertThat(TestChatModel.lastRequest.messages()).filteredOn(UserMessage.class::isInstance).hasSize(1);
        assertThat(TestChatMemoryStore.MESSAGES.get(memoryId))
                .containsExactlyElementsOf(List.of(
                        suspendedConversation.get(0),
                        suspendedConversation.get(1),
                        suspendedConversation.get(2),
                        AiMessage.from("Conversation continued")));
    }

    @Test
    @ActivateRequestContext
    void resumesFromGeneralConversationHistory() {
        String memoryId = "general-conversation";
        List<ChatMessage> conversation = List.of(
                UserMessage.from("Summarize our discussion"),
                AiMessage.from("We discussed deployment options."));
        TestChatMemoryStore.MESSAGES.put(memoryId, new ArrayList<>(conversation));

        assertThat(assistant.resume(memoryId)).isEqualTo("Conversation continued");
        assertThat(TestChatModel.lastRequest.messages()).containsExactlyElementsOf(conversation);
    }

    @Test
    @ActivateRequestContext
    void rejectsEmptyConversation() {
        assertThatThrownBy(() -> assistant.resume("empty-conversation"))
                .hasMessageContaining("Cannot resume a conversation with empty chat memory");
    }

    @RegisterAiService(chatLanguageModelSupplier = TestChatModelSupplier.class, chatMemoryProviderSupplier = TestChatMemoryProviderSupplier.class)
    interface Assistant {

        @ResumeConversation
        String resume(@MemoryId String memoryId);
    }

    public static class TestChatMemoryStore implements ChatMemoryStore {

        static final Map<Object, List<ChatMessage>> MESSAGES = new ConcurrentHashMap<>();

        @Override
        public List<ChatMessage> getMessages(Object memoryId) {
            return new ArrayList<>(MESSAGES.getOrDefault(memoryId, List.of()));
        }

        @Override
        public void updateMessages(Object memoryId, List<ChatMessage> messages) {
            MESSAGES.put(memoryId, new ArrayList<>(messages));
        }

        @Override
        public void deleteMessages(Object memoryId) {
            MESSAGES.remove(memoryId);
        }
    }

    public static class TestChatMemoryProviderSupplier implements Supplier<ChatMemoryProvider> {

        @Override
        public ChatMemoryProvider get() {
            return memoryId -> MessageWindowChatMemory.builder()
                    .id(memoryId)
                    .maxMessages(20)
                    .chatMemoryStore(new TestChatMemoryStore())
                    .build();
        }
    }

    public static class TestChatModelSupplier implements Supplier<ChatModel> {

        @Override
        public ChatModel get() {
            return new TestChatModel();
        }
    }

    public static class TestChatModel implements ChatModel {

        static ChatRequest lastRequest;

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            lastRequest = chatRequest;
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("Conversation continued"))
                    .build();
        }
    }
}
