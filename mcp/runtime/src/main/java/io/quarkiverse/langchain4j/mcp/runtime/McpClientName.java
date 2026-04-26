package io.quarkiverse.langchain4j.mcp.runtime;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Qualifier;

/**
 * This annotation has two uses:
 * - On an injection point of type McpClient, this can be used as a qualifier to pick a particular MCP client by its name.
 * - When applied on a bean that implements McpClientAuthProvider, it indicates that this provider is associated with the named
 * MCP client. In this case,
 * it's allowed to add multiple McpClientName annotations to say that the provider should be used for multiple MCP clients.
 */
@Target({ ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER })
@Retention(RUNTIME)
@Documented
@Qualifier
@Repeatable(McpClientNames.class)
public @interface McpClientName {

    String value();

    class Literal extends AnnotationLiteral<McpClientName> implements McpClientName {

        public static Literal of(String value) {
            return new Literal(value);
        }

        private final String value;

        public Literal(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }
    }
}
