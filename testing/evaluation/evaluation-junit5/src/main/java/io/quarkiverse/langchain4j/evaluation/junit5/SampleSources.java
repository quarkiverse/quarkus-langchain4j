package io.quarkiverse.langchain4j.evaluation.junit5;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for combining multiple sample sources in a single test.
 * <p>
 * This annotation allows tests to load and combine samples from multiple files or sources.
 * All samples are merged into a single {@link io.quarkiverse.langchain4j.testing.evaluation.Samples} instance.
 * </p>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface SampleSources {

    /**
     * Array of sample locations to load and combine.
     * <p>
     * Samples from all locations will be merged into a single Samples instance.
     * The order of samples in the combined result is the order they appear in the sources.
     * </p>
     *
     * @return array of sample locations
     */
    SampleLocation[] value();
}
