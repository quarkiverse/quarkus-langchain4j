package io.quarkiverse.langchain4j.compaction.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.logging.Level;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.compaction.runtime.CompactionProfiles;
import io.quarkiverse.langchain4j.compaction.runtime.Retention;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Retention is configured per profile, and the startup log says that compaction provides the chat memory and that
 * {@code quarkus.langchain4j.chat-memory.*} is ignored.
 */
class RetentionConfigTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .withApplicationRoot(jar -> jar.addClasses(CompactionProfilesTest.FakeModel.class))
            .overrideConfigKey("quarkus.langchain4j.compaction.retention.max-messages", "200")
            .overrideConfigKey("quarkus.langchain4j.compaction.claims.strategy", "token-window")
            .overrideConfigKey("quarkus.langchain4j.compaction.claims.retention.max-age", "30d")
            .overrideConfigKey("quarkus.langchain4j.chat-memory.memory-window.max-messages", "5")
            .setLogRecordPredicate(r -> r.getLoggerName().equals(CompactionProfiles.class.getName()))
            .assertLogRecords(records -> {
                assertThat(records).anySatisfy(r -> {
                    assertThat(r.getLevel()).isEqualTo(Level.INFO);
                    assertThat(r.getMessage()).contains("provides the chat memory of the AI services");
                });
                assertThat(records).anySatisfy(r -> {
                    assertThat(r.getLevel()).isEqualTo(Level.WARNING);
                    assertThat(r.getParameters()[0].toString())
                            .contains("quarkus.langchain4j.chat-memory.memory-window.max-messages");
                });
            });

    @Inject
    CompactionProfiles profiles;

    @Test
    void configuresTheRetentionOfEachProfile() {
        Retention defaults = profiles.defaultProvider().components().retention();
        assertThat(defaults.maxMessages()).hasValue(200);
        assertThat(defaults.maxAge()).isEmpty();
        Retention claims = profiles.provider("claims").components().retention();
        assertThat(claims.maxMessages()).isEmpty();
        assertThat(claims.maxAge()).contains(Duration.ofDays(30));
    }
}
