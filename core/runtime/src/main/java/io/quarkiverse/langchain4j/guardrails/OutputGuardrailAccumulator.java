package io.quarkiverse.langchain4j.guardrails;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * An annotation to configure token accumulation when output guardrails are applied on streamed responses.
 * <p>
 * The accumulator is invoked before the guardrails are applied to the output of the model, and invoke the guardrail chain on
 * the accumulated tokens.
 * The guardrail may be called multiple time depending on the accumulation strategy.
 * <p>
 * If the annotation is not present and the output is streamed, the default behavior is to accumulate all the tokens before
 * applying the guardrails.
 */
@Retention(RUNTIME)
@Target({ ElementType.METHOD })
public @interface OutputGuardrailAccumulator {

    /**
     * The class of the CDI bean implementing the {@link OutputTokenAccumulator} interface.
     */
    Class<? extends OutputTokenAccumulator> value();

}
