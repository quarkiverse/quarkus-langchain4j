package io.quarkiverse.langchain4j.observability;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.inject.Qualifier;

/**
 * Annotation used to select a particular AiService interface. The interface specified in {@link #value()} must be annotated
 * with
 * {@link io.quarkiverse.langchain4j.RegisterAiService RegisterAiService}.
 */
@Qualifier
@Target({ METHOD, FIELD, PARAMETER, TYPE })
@Retention(RUNTIME)
public @interface AiServiceSelector {
    /**
     * Specifies the interface class annotated with {@link io.quarkiverse.langchain4j.RegisterAiService RegisterAiService}.
     */
    Class<?> value();
}
