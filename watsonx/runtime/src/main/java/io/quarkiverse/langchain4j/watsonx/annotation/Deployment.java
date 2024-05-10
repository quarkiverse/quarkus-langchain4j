package io.quarkiverse.langchain4j.watsonx.annotation;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 *
 */
@Retention(RUNTIME)
@Target(ElementType.TYPE)
public @interface Deployment {

    /**
     * The id or the name that identifies the deployment.
     */
    String value();

    /**
     *
     */
    String chatMemory() default "";
}
