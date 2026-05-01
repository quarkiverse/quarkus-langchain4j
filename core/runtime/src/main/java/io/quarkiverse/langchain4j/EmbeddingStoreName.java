package io.quarkiverse.langchain4j;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Qualifier;

/**
 * Marker annotation to select a named embedding store.
 * Configure the {@code value} parameter to select the embedding store instance.
 * <p>
 * Currently, only the pgvector extension supports named embedding stores.
 * Other embedding store providers will need to implement the same capability.
 * <p>
 * For example, when configuring pgvector like so:
 *
 * <pre>
 * quarkus.langchain4j.pgvector.products.dimension=1536
 * quarkus.langchain4j.pgvector.products.datasource=products-ds
 * </pre>
 *
 * Then to inject the proper {@code EmbeddingStore}, you would use {@code EmbeddingStoreName} like so:
 *
 * <pre>
 * &#64;Inject
 * &#64;EmbeddingStoreName("products")
 * EmbeddingStore&lt;TextSegment&gt; store;
 * </pre>
 */
@Target({ ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER })
@Retention(RUNTIME)
@Documented
@Qualifier
public @interface EmbeddingStoreName {
    /**
     * Specify the name of the embedding store.
     *
     * @return the value
     */
    String value() default "";

    class Literal extends AnnotationLiteral<EmbeddingStoreName> implements EmbeddingStoreName {

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
