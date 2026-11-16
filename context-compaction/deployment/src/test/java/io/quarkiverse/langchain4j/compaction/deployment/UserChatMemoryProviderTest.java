package io.quarkiverse.langchain4j.compaction.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.compaction.CompactionProfiles;
import io.quarkiverse.langchain4j.compaction.runtime.CompactingChatMemoryProvider;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * An application that declares its own ChatMemoryProvider bean keeps it: the extension does not replace it, and the
 * compaction profiles remain available programmatically.
 */
class UserChatMemoryProviderTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .withApplicationRoot(jar -> jar.addClasses(Assistant.class, FakeModel.class, FakeModelSupplier.class,
                    MyMemoryProvider.class));

    @RegisterAiService(chatLanguageModelSupplier = FakeModelSupplier.class)
    interface Assistant {
        String chat(@MemoryId String id, @UserMessage String question);
    }

    @ApplicationScoped
    public static class MyMemoryProvider implements ChatMemoryProvider {
        @Override
        public ChatMemory get(Object memoryId) {
            return MessageWindowChatMemory.builder().id(memoryId).maxMessages(4).build();
        }
    }

    @Singleton
    public static class FakeModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest request) {
            return ChatResponse.builder().aiMessage(AiMessage.from("ok")).build();
        }
    }

    public static class FakeModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return io.quarkus.arc.Arc.container().instance(FakeModel.class).get();
        }
    }

    @Inject
    Assistant assistant;

    @Inject
    ChatMemoryProvider provider;

    @Inject
    CompactionProfiles profiles;

    @Test
    @ActivateRequestContext
    void keepsTheApplicationProvider() {
        assertThat(assistant.chat("c1", "hello")).isEqualTo("ok");
        assertThat(provider.get("c1")).isInstanceOf(MessageWindowChatMemory.class);
        assertThat(profiles.defaultProvider()).isInstanceOf(CompactingChatMemoryProvider.class);
    }
}
