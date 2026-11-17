package io.quarkiverse.langchain4j.compaction.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import io.quarkiverse.langchain4j.compaction.runtime.CompactionClock;
import io.quarkiverse.langchain4j.compaction.runtime.DefaultCompactionProfiles;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * A {@link CompactionClock} bean replaces the system clock: message ages, and so the retention, follow it. An
 * unqualified application {@link Clock} is ignored.
 */
class ApplicationClockTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .withApplicationRoot(jar -> jar.addClasses(CompactionProfilesTest.FakeModel.class, MutableClock.class,
                    BusinessClock.class))
            .overrideConfigKey("quarkus.langchain4j.compaction.retention.max-age", "1h");

    @Singleton
    @CompactionClock
    public static class MutableClock extends Clock {
        volatile Instant now = Instant.parse("2026-01-01T09:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    // an unqualified application clock neither conflicts with the compaction clock nor replaces it
    public static class BusinessClock {
        @Produces
        @Singleton
        Clock clock() {
            return Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        }
    }

    @Inject
    DefaultCompactionProfiles profiles;

    @Inject
    @CompactionClock
    MutableClock clock;

    @Test
    void usesTheApplicationClock() {
        assertThat(profiles.defaultProvider().components().clock()).isSameAs(clock);

        ChatMemory memory = profiles.defaultProvider().get("clock");
        memory.add(UserMessage.from("first"));
        clock.now = clock.now.plus(Duration.ofHours(2));
        memory.add(UserMessage.from("second"));

        // two hours later on the application clock, the first message is past the one-hour retention
        assertThat(memory.messages()).extracting(m -> ((UserMessage) m).singleText()).containsExactly("second");
    }
}
