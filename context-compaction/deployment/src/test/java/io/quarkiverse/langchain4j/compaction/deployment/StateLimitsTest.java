package io.quarkiverse.langchain4j.compaction.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Supplier;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.compaction.CompactionInspector;
import io.quarkiverse.langchain4j.compaction.runtime.CompactionProfiles;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * The in-memory compaction state is bounded per profile, and inspector snapshots can be turned off.
 */
class StateLimitsTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .withApplicationRoot(jar -> jar.addClasses(Assistant.class, FakeModel.class, FakeModelSupplier.class))
            .overrideConfigKey("quarkus.langchain4j.compaction.state.max-conversations", "2")
            .overrideConfigKey("quarkus.langchain4j.compaction.inspector-snapshots", "false");

    @RegisterAiService(chatLanguageModelSupplier = FakeModelSupplier.class)
    interface Assistant {
        String chat(@MemoryId String id, @UserMessage String question);
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
    CompactionProfiles profiles;

    @Inject
    CompactionInspector inspector;

    @Inject
    ChatMemoryStore store;

    @Test
    @ActivateRequestContext
    void keepsTheStateOfAtMostTwoConversations() {
        for (int i = 0; i < 5; i++) {
            assistant.chat("conversation-" + i, "hello");
        }
        assertThat(profiles.defaultProvider().components().states().size()).isLessThanOrEqualTo(2);
        assertThat(inspector.snapshot("conversation-4")).isEmpty();
        // the histories are untouched
        for (int i = 0; i < 5; i++) {
            assertThat(store.getMessages("conversation-" + i)).hasSize(2);
        }
    }
}
