package io.quarkiverse.langchain4j.evaluation.junit5;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Allows configuring the location of the Yaml file defining the sample.
 * The value of this annotation should be the path to the Yaml file.
 * The target of this annotation should be a parameter of type {@link io.quarkiverse.langchain4j.testing.evaluation.Samples}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface SampleLocation {

    /**
     * @return the location of the Yaml file defining the sample.
     */
    String value();

}
