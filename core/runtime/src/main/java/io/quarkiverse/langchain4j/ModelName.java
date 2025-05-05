package io.quarkiverse.langchain4j;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Qualifier;

/**
 * Marker annotation to select a named model
 * Configure the {@code name} parameter to select the model instance.
 * <p>
 * For example, when configuring OpenAI like so:
 *
 * <pre>
 * quarkus.langchain4j.openai.somename.api-key=somekey
 * </pre>
 *
 * Then to inject the proper {@code ChatLanguageModel}, you would need to use {@code Model} like so:
 *
 * <pre>
 *     &#64Inject
 *     &#ModelName("somename")
 *     ChatModel model;
 * </pre>
 *
 * For the case of {@link RegisterAiService}, instead of using this annotation, users should set the {@code modelName} property
 * instead.
 */
@Target({ ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER })
@Retention(RUNTIME)
@Documented
@Qualifier
public @interface ModelName {
    /**
     * Specify the cluster name of the connection.
     *
     * @return the value
     */
    String value() default "";

    class Literal extends AnnotationLiteral<ModelName> implements ModelName {

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
