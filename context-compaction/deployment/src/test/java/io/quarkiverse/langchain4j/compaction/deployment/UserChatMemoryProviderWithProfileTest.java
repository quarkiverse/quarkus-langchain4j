package io.quarkiverse.langchain4j.compaction.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.compaction.CompactionProfile;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * {@code @CompactionProfile} cannot take effect when the application declares its own ChatMemoryProvider: the build
 * fails instead of silently ignoring the annotation.
 */
class UserChatMemoryProviderWithProfileTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .withApplicationRoot(jar -> jar.addClasses(Claims.class, UserChatMemoryProviderTest.FakeModel.class,
                    UserChatMemoryProviderTest.FakeModelSupplier.class, UserChatMemoryProviderTest.MyMemoryProvider.class))
            .overrideConfigKey("quarkus.langchain4j.compaction.claims.strategy", "token-window")
            .assertException(e -> assertThat(e).hasStackTraceContaining("@CompactionProfile on")
                    .hasStackTraceContaining("declares its own ChatMemoryProvider"));

    @RegisterAiService(chatLanguageModelSupplier = UserChatMemoryProviderTest.FakeModelSupplier.class)
    @CompactionProfile("claims")
    interface Claims {
        String chat(@MemoryId String id, @UserMessage String question);
    }

    @Test
    void failsTheBuild() {
        // the assertion is in assertException
    }
}
