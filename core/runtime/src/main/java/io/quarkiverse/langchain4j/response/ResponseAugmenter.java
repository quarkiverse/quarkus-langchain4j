package io.quarkiverse.langchain4j.response;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import io.smallrye.common.annotation.Experimental;

/**
 * An annotation to configure a response augmenter.
 * <p>
 * Response augmenter have the ability to update the output of the model before sending the results to the caller.
 * The augmenter is invoked after the output guardrails are applied to the output of the model and after the parsing / mapping
 * of the response to a business object.
 * <p>
 * The augmenter must be a CDI bean and the class name must implement the {@link AiResponseAugmenter} interface.
 */
@Retention(RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
@Experimental("This API is subject to change")
public @interface ResponseAugmenter {

    /**
     * The class of the CDI bean implementing the {@link AiResponseAugmenter} interface.
     */
    Class<? extends AiResponseAugmenter<?>> value();
}
