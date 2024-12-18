package io.quarkiverse.langchain4j.scorer.junit5;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Allows configuring the number of threads to use for the evaluation.
 * The target of this annotation should be a parameter or a field of type
 * {@link io.quarkiverse.langchain4j.testing.scorer.Scorer}.
 */
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
public @interface ScorerConfiguration {

    /**
     * @return the number of threads to use for the evaluation.
     */
    int concurrency() default 1;

}
