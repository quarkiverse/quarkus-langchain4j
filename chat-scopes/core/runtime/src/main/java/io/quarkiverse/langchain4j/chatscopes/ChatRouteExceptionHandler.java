package io.quarkiverse.langchain4j.chatscopes;

import java.lang.annotation.*;

/**
 * Mark a method as a chat route exception handler.
 * The method must take a single parameter of exception you want to catch.
 *
 * The value of this annotation allows you to have different handlers for same
 * exception type for different routes.
 *
 * Specifying no routes means the handler will be used for entire application.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD })
public @interface ChatRouteExceptionHandler {
    String[] value() default {};
}
