package io.quarkiverse.langchain4j.compaction;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Selects the compaction profile of an AI service, so services of the same application can use different strategies,
 * budgets and options:
 *
 * <pre>
 * &#64;RegisterAiService
 * &#64;CompactionProfile("claims")
 * interface ClaimsAgent { ... }
 * </pre>
 *
 * configured with {@code quarkus.langchain4j.compaction.claims.*}. AI services without this annotation use the default
 * profile, configured directly under {@code quarkus.langchain4j.compaction}.
 * <p>
 * The annotation only applies to AI services using the application chat memory provider (the default). A service with
 * its own {@code chatMemoryProviderSupplier} can call
 * {@link io.quarkiverse.langchain4j.compaction.runtime.CompactionProfiles#provider(String)} instead.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CompactionProfile {

    /**
     * @return the profile name
     */
    String value();
}
