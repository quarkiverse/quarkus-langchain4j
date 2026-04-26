package io.quarkiverse.langchain4j.chatscopes;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Applied to a chat route method to markt it as the default route for the entire applicaton.
 *
 * If clients do not specify an initial route when starting a session, the default route will be used.
 */
@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DefaultChatRoute {

}
