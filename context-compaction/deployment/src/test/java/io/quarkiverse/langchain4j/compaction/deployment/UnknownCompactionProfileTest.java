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
 * A profile referenced by an AI service but not configured (here, a typo) fails the application start.
 */
class UnknownCompactionProfileTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .withApplicationRoot(jar -> jar.addClasses(Claims.class, CompactionProfilesTest.FakeModel.class,
                    CompactionProfilesTest.FakeModelSupplier.class))
            .overrideConfigKey("quarkus.langchain4j.compaction.claims.strategy", "token-window")
            .assertException(e -> assertThat(e).hasStackTraceContaining("compaction profile 'claim'")
                    .hasStackTraceContaining("configured profiles: [claims]"));

    @RegisterAiService(chatLanguageModelSupplier = CompactionProfilesTest.FakeModelSupplier.class)
    @CompactionProfile("claim")
    interface Claims {
        String chat(@MemoryId String id, @UserMessage String question);
    }

    @Test
    void failsTheStart() {
        // the assertion is in assertException
    }
}
