package io.quarkiverse.langchain4j.chatscopes;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.enterprise.context.NormalScope;

/**
 * Defines a CDI chat scope conversation bean that is unique to each chat scope. Unlike {@link ChatScoped}, nested chat scopes
 * do not inherit @PerChatScoped beans from their parent scopes.
 *
 * @see ChatScope
 * @see ChatRoutes
 * @see WebsocketChatRoutes
 */
@Documented
@NormalScope
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD, ElementType.FIELD })
public @interface PerChatScoped {

}
