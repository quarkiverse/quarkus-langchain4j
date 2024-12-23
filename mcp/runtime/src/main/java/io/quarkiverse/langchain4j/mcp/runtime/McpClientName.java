package io.quarkiverse.langchain4j.mcp.runtime;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Qualifier;

/**
 * Used as a qualifier to denote a particular MCP client by its name.
 */
@Target({ ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER })
@Retention(RUNTIME)
@Documented
@Qualifier
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
