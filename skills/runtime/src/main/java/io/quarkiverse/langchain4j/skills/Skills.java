package io.quarkiverse.langchain4j.skills;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that an AI service or agent should use skills.
 * <p>
 * When used without arguments ({@code @Skills}), the service/agent will have access to all loaded skills.
 * When used with skill names ({@code @Skills("trip-planner", "weather")}), only the named skills will be available.
 * <p>
 * This annotation can be placed on:
 * <ul>
 * <li>A {@code @RegisterAiService} interface (type-level) to configure skills for the AI service</li>
 * <li>An {@code @Agent}-annotated method (method-level) to configure skills for that specific agent</li>
 * </ul>
 * <p>
 * If any referenced skill name does not exist among the loaded skills, the application will fail at startup.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
public @interface Skills {

    /**
     * The names of the skills to make available. An empty array (the default) means all loaded skills.
     */
    String[] value() default {};
}
