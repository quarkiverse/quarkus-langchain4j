package io.quarkiverse.langchain4j.runtime.decision;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Qualifier;

/**
 * Qualifies the {@link QuarkusDecisionServiceContext} of a Decision Service with the name of its interface.
 */
@Qualifier
@Target({ PARAMETER })
@Retention(RUNTIME)
public @interface QuarkusDecisionServiceContextQualifier {

    /**
     * The name of the interface of the Decision Service.
     */
    String value();

    class Literal extends AnnotationLiteral<QuarkusDecisionServiceContextQualifier>
            implements QuarkusDecisionServiceContextQualifier {

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
