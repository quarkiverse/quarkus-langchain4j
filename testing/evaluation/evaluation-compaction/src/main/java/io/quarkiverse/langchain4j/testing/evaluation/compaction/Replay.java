package io.quarkiverse.langchain4j.testing.evaluation.compaction;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import io.quarkiverse.langchain4j.compaction.CompactionReport;
import io.quarkiverse.langchain4j.compaction.runtime.CompactionClock;
import io.quarkus.arc.Unremovable;

/**
 * Replaces the compaction clock with one an evaluation moves along the recorded timestamps, and collects the
 * compaction reports of the evaluation. Outside an evaluation, the clock is the system clock.
 */
@Singleton
@Unremovable
public class Replay {

    private volatile Instant now;
    private final List<CompactionReport> reports = new CopyOnWriteArrayList<>();

    @Produces
    @Singleton
    @CompactionClock
    Clock clock() {
        return new Clock() {
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
                Instant replayed = now;
                return replayed != null ? replayed : Instant.now();
            }
        };
    }

    void start(Instant start) {
        reports.clear();
        now = start;
    }

    void moveTo(Instant instant) {
        now = instant;
    }

    List<CompactionReport> stop() {
        now = null;
        List<CompactionReport> collected = List.copyOf(reports);
        reports.clear();
        return collected;
    }

    void onCompaction(@Observes CompactionReport report) {
        if (now != null) {
            reports.add(report);
        }
    }
}
