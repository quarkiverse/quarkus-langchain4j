package io.quarkiverse.langchain4j.a2a.runtime.apicurio;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Place this annotation on an AI Service (registered via {@link io.quarkiverse.langchain4j.RegisterAiService})
 * to publish its agent card to Apicurio Registry on startup.
 */
@Retention(RUNTIME)
@Target(ElementType.TYPE)
public @interface PublishToAgentRegistry {

    String name();

    String description();

    String version() default "1.0.0";

    Skill[] skills() default {};

    @Retention(RUNTIME)
    @Target(ElementType.ANNOTATION_TYPE)
    @interface Skill {

        String id();

        String name();

        String description();
    }
}
