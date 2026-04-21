package io.quarkiverse.langchain4j.chatscopes;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * When applied to a class, it specifies what the event type will be when the class is returned as a result
 * of a chat route method, or if ResponseChannel.event(Object) is called.
 *
 * This can also be applied to a chat route method to specify the event type to use for the return value of the method.
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EventType {
    String value();
}
