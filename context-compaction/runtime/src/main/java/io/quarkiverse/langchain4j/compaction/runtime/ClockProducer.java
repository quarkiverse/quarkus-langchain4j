package io.quarkiverse.langchain4j.compaction.runtime;

import java.time.Clock;

import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.Reserve;
import jakarta.inject.Singleton;

/**
 * Produces the {@link Clock} of compaction (message ages, time decay, retention, idle eviction): the UTC system clock.
 */
public class ClockProducer {

    @Produces
    @Singleton
    @CompactionClock
    @Reserve
    @Priority(0)
    Clock clock() {
        return Clock.systemUTC();
    }
}
