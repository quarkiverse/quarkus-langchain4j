package io.quarkiverse.langchain4j.evaluation.junit5;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Allows configuring the number of threads and the number of trials per sample to use for the evaluation.
 * The target of this annotation should be a parameter or a field of type
 * {@link io.quarkiverse.langchain4j.testing.evaluation.Scorer}.
 */
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
public @interface ScorerConfiguration {

    /**
     * @return the number of threads to use for the evaluation.
     */
    int concurrency() default 1;

    /**
     * @return the number of times each sample is executed and evaluated. Running each sample several times
     *         makes the score and its confidence interval meaningful when the function under test is not deterministic.
     */
    int trials() default 1;

}
