package io.quarkiverse.langchain4j.tests.scopes.chat;

import static io.quarkiverse.langchain4j.runtime.LangChain4jUtil.chatMessageToText;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.chatscopes.ChatScope;
import io.quarkiverse.langchain4j.chatscopes.ChatScoped;
import io.quarkus.test.QuarkusUnitTest;

public class ChatScopeMemoryTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClasses(AiService.class, MirrorModelSupplier.class,
                            CustomChatMemoryStore.class));

    public static class MirrorModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest chatRequest) {
                    return ChatResponse.builder()
                            .aiMessage(new AiMessage(chatMessageToText(chatRequest.messages().get(0))))
                            .build();
                }
            };
        }
    }

    @RegisterAiService(chatLanguageModelSupplier = MirrorModelSupplier.class)
    @ChatScoped
    interface AiService {
        String chat(@UserMessage String userMessage);
    }

    @ApplicationScoped
    public static class CustomChatMemoryStore implements ChatMemoryStore {

        private final Map<Object, List<ChatMessage>> messagesByMemoryId = new ConcurrentHashMap<>();

        public Map<Object, List<ChatMessage>> all() {
            return messagesByMemoryId;
        }

        @Override
        public List<ChatMessage> getMessages(Object memoryId) {
            return messagesByMemoryId.computeIfAbsent(memoryId, ignored -> new ArrayList<>());
        }

        @Override
        public void updateMessages(Object memoryId, List<ChatMessage> messages) {
            messagesByMemoryId.put(memoryId, messages);
        }

        @Override
        public void deleteMessages(Object memoryId) {
            messagesByMemoryId.remove(memoryId);
        }
    }

    @Inject
    CustomChatMemoryStore customChatMemoryStore;

    @Inject
    AiService aiService;

    @Test
    public void testDefaultMemoryId() {
        ChatScope.begin();

        aiService.chat("Hello");
        Assertions.assertEquals(1, customChatMemoryStore.all().size());
        Assertions.assertTrue(((String) customChatMemoryStore.all().keySet().iterator().next()).startsWith(ChatScope.id()));

        ChatScope.end();
        Assertions.assertEquals(0, customChatMemoryStore.all().size());

    }

}
