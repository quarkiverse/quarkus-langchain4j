package io.quarkiverse.langchain4j.compaction;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.smallrye.common.annotation.Experimental;

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
 * configured with {@code quarkus.langchain4j.compaction.profiles.claims.*}. AI services without this annotation use the default
 * profile, configured directly under {@code quarkus.langchain4j.compaction}.
 * <p>
 * The annotation only applies to AI services using the application chat memory provider (the default). A service with
 * its own {@code chatMemoryProviderSupplier} can call
 * {@link CompactionProfiles#provider(String)} instead.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Experimental("This feature is experimental and the API is subject to change")
public @interface CompactionProfile {

    /**
     * @return the profile name
     */
    String value();
}
