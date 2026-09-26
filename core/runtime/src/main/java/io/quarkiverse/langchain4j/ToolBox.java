package io.quarkiverse.langchain4j;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * When used on a method of an AiService annotated with {@link RegisterAiService}, the method will use the tool classes provided by
 * {@code value}
 * instead of the ones configured for the entire AiService (via {@link RegisterAiService#tools()})
 */
@Retention(RUNTIME)
@Target({ METHOD })
public @interface ToolBox {

    /**
     * Tool classes to use. All tools are expected to be CDI beans.
     */
    Class[] value() default {};
}
