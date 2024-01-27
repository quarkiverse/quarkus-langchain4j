package io.quarkiverse.langchain4j.runtime.aiservice;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Qualifier;

@Qualifier
@Inherited
@Target({ PARAMETER })
@Retention(RUNTIME)
public @interface QuarkusAiServiceContextQualifier {

    /**
     * The name of class
     */
    String value();

    class Literal extends AnnotationLiteral<QuarkusAiServiceContextQualifier> implements QuarkusAiServiceContextQualifier {

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
