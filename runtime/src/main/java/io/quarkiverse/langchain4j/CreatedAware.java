package io.quarkiverse.langchain4j;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Used to make Quarkus aware of classes being used in {@link dev.langchain4j.service.AiServices#create}
 */
@Retention(RUNTIME)
@Target(ElementType.TYPE)
public @interface CreatedAware {
}
