package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatMemoryProviderSupplier;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.chatscopes.ChatScope;
import io.quarkiverse.langchain4j.chatscopes.ChatScoped;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;

public class ChatScopedAgentMemoryTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ChatAgent.class, InMemoryChatMemoryStore.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    public interface ChatAgent {

        @UserMessage("{{message}}")
        @Agent(description = "A simple chat agent")
        @ChatScoped
        String chat(@V("message") String message);

        @ChatMemoryProviderSupplier
        static ChatMemory chatMemory(Object memoryId) {
            return MessageWindowChatMemory.builder()
                    .id(memoryId)
                    .maxMessages(10)
                    .chatMemoryStore(Arc.container().select(InMemoryChatMemoryStore.class).get())
                    .build();
        }

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest request) {
                    return ChatResponse.builder()
                            .aiMessage(new AiMessage("echo"))
                            .build();
                }
            };
        }
    }

    @ApplicationScoped
    public static class InMemoryChatMemoryStore implements ChatMemoryStore {

        private final Map<Object, List<ChatMessage>> store = new ConcurrentHashMap<>();

        public Map<Object, List<ChatMessage>> all() {
            return store;
        }

        @Override
        public List<ChatMessage> getMessages(Object memoryId) {
            return store.computeIfAbsent(memoryId, k -> new ArrayList<>());
        }

        @Override
        public void updateMessages(Object memoryId, List<ChatMessage> messages) {
            store.put(memoryId, messages);
        }

        @Override
        public void deleteMessages(Object memoryId) {
            store.remove(memoryId);
        }
    }

    @Inject
    ChatAgent chatAgent;

    @Inject
    InMemoryChatMemoryStore memoryStore;

    @BeforeEach
    void reset() {
        memoryStore.all().clear();
    }

    @Test
    void memoryIdDerivedFromChatScope() {
        ChatScope.begin();
        String scopeId = ChatScope.id();

        chatAgent.chat("hello");

        assertThat(memoryStore.all()).hasSize(1);
        String memoryId = (String) memoryStore.all().keySet().iterator().next();
        assertThat(memoryId).startsWith(scopeId);

        ChatScope.end();
    }

    @Test
    void differentScopesUseDifferentMemoryIds() {
        ChatScope.begin();
        String firstScopeId = ChatScope.id();
        chatAgent.chat("first");
        String firstMemoryId = (String) memoryStore.all().keySet().iterator().next();
        ChatScope.end();

        ChatScope.begin();
        String secondScopeId = ChatScope.id();
        chatAgent.chat("second");
        ChatScope.end();

        assertThat(firstScopeId).isNotEqualTo(secondScopeId);
        assertThat(firstMemoryId).startsWith(firstScopeId);
        assertThat(memoryStore.all().keySet())
                .anySatisfy(key -> assertThat((String) key).startsWith(secondScopeId));
    }
}
