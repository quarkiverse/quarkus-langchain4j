package io.quarkiverse.langchain4j;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention(RUNTIME)
@Target(ElementType.TYPE)
public @interface RagPipeline {

    /**
     * A pre-built {@link dev.langchain4j.rag.RetrievalAugmentor} CDI bean to use directly,
     * bypassing decomposed pipeline composition.
     * <p>
     * When set, all other attributes ({@code retrievers}, {@code router}, etc.) must be absent.
     * <p>
     * <b>Context propagation note:</b> the pre-built augmentor is responsible for its own executor.
     * If it uses {@link dev.langchain4j.rag.DefaultRetrievalAugmentor} internally, supply a
     * {@code ManagedExecutor} to its builder to ensure CDI request context, OTel spans, and
     * security context propagate across parallel retrieval threads. The decomposed mode
     * ({@code retrievers}, {@code router}) handles this automatically.
     */
    Class<?> augmentor() default void.class;

    Class<?>[] retrievers() default {};

    Class<?> router() default void.class;

    Class<?> transformer() default void.class;

    Class<?> aggregator() default void.class;

    Class<?> injector() default void.class;
}
