package io.quarkiverse.langchain4j.compaction.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.compaction.CompactionProfile;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * The profile would be silently ignored on a service bringing its own chat memory provider, so the build fails.
 */
class CompactionProfileWithCustomSupplierTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .withApplicationRoot(jar -> jar.addClasses(Claims.class, OwnMemory.class,
                    CompactionProfilesTest.FakeModel.class, CompactionProfilesTest.FakeModelSupplier.class))
            .overrideConfigKey("quarkus.langchain4j.compaction.claims.strategy", "token-window")
            .assertException(e -> assertThat(e).hasStackTraceContaining("has no effect with a custom"));

    @RegisterAiService(chatLanguageModelSupplier = CompactionProfilesTest.FakeModelSupplier.class, chatMemoryProviderSupplier = OwnMemory.class)
    @CompactionProfile("claims")
    interface Claims {
        String chat(@MemoryId String id, @UserMessage String question);
    }

    public static class OwnMemory implements Supplier<ChatMemoryProvider> {
        @Override
        public ChatMemoryProvider get() {
            return id -> MessageWindowChatMemory.withMaxMessages(10);
        }
    }

    @Test
    void failsTheBuild() {
        // the assertion is in assertException
    }
}
