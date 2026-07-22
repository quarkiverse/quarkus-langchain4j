package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Supplier;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

public class MemoryConfigProviderStoreConflictTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ConfiguredService.class, ModelSupplier.class, MemoryProvider.class, MemoryStore.class))
            .assertException(error -> assertThat(error)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot combine @RegisterAiService.chatMemoryProvider with chatMemoryStore"));

    @RegisterAiService(chatLanguageModelSupplier = ModelSupplier.class, chatMemoryProvider = MemoryProvider.class, chatMemoryStore = MemoryStore.class)
    interface ConfiguredService {
        String chat(String message);
    }

    public static class ModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return null;
        }
    }

    public static class MemoryProvider implements ChatMemoryProvider {
        @Override
        public ChatMemory get(Object memoryId) {
            return null;
        }
    }

    public static class MemoryStore implements ChatMemoryStore {
        @Override
        public List<ChatMessage> getMessages(Object memoryId) {
            return List.of();
        }

        @Override
        public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        }

        @Override
        public void deleteMessages(Object memoryId) {
        }
    }

    @Test
    void rejectsProviderAndStoreCombination() {
        // Asserted while the test application starts.
    }
}
