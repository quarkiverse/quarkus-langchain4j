package io.quarkiverse.langchain4j.compaction.runtime;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.inject.Qualifier;

/**
 * Qualifies the internal {@link java.time.Clock} of compaction (message ages, time decay, retention, idle eviction),
 * so it does not conflict with an application {@code Clock} bean. The evaluation replaces it to replay recorded
 * timestamps; it is not a supported extension point.
 */
@Qualifier
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER })
public @interface CompactionClock {
}
