package io.quarkiverse.langchain4j.a2a.server;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import io.smallrye.common.annotation.Experimental;

/**
 * This annotation is meant to be placed on an AI Service (registered via {@link io.quarkiverse.langchain4j.RegisterAiService})
 * to expose it as an A2A server
 */
@Retention(RUNTIME)
@Target(ElementType.TYPE)
@Experimental("The A2A story still has a lot of details to be fleshed out")
public @interface ExposeA2AAgent {

    String name();

    String description();

    boolean streaming() default false;

    boolean pushNotifications() default false;

    boolean stateTransitionHistory() default false;

    Skill[] skills();

    @Retention(RUNTIME)
    @Target(ElementType.ANNOTATION_TYPE)
    @interface Skill {

        String id();

        String name();

        String description();

        String[] tags() default {};

        String[] examples() default {};
    }
}
