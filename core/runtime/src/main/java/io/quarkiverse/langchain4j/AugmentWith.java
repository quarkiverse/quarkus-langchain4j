package io.quarkiverse.langchain4j;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.function.Supplier;

import dev.langchain4j.rag.RetrievalAugmentor;

/**
 * Configures the {@link RetrievalAugmentor} to use for a specific method of an AI service, overriding the
 * class-level {@link RegisterAiService#retrievalAugmentor()} configuration.
 * <p>
 * Methods without this annotation keep the behavior defined by {@link RegisterAiService#retrievalAugmentor()}.
 * <p>
 * The supplier may or may not be a CDI bean. If it is not a CDI bean, Quarkus will create an instance
 * by calling its no-arg constructor.
 * <p>
 * Use {@link None} to explicitly disable retrieval augmentation for a method, even when an augmentor is
 * configured at the class level. This avoids wasting retrieval calls on methods that don't need them.
 * <p>
 * Ignored on {@code default} methods and on image generation methods.
 */
@Retention(RUNTIME)
@Target(ElementType.METHOD)
public @interface AugmentWith {

    /**
     * The supplier of the {@link RetrievalAugmentor} to use for the annotated method.
     */
    Class<? extends Supplier<RetrievalAugmentor>> value();

    /**
     * Marker that is used when no retrieval augmentation should be performed for the annotated method,
     * even if the enclosing AI service is configured with one.
     */
    final class None implements Supplier<RetrievalAugmentor> {

        @Override
        public RetrievalAugmentor get() {
            throw new UnsupportedOperationException("should never be called");
        }
    }
}
