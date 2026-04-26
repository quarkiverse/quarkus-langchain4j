package io.quarkiverse.langchain4j.chatscopes;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.enterprise.context.NormalScope;

/**
 * Defines a CDI chat scope conversation bean. Nested chat scopes inherit chat scoped beans from their parent scopes if they
 * have been allocated there. Calling {@link ChatScope#pop()} will destroy any chat scoped beans allocated in the current scope.
 *
 * @see ChatScope
 */
@Documented
@NormalScope
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD, ElementType.FIELD })
public @interface ChatScoped {

}
