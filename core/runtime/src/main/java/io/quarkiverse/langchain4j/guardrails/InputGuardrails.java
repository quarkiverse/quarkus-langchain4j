package io.quarkiverse.langchain4j.guardrails;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import io.quarkiverse.langchain4j.runtime.aiservice.GuardrailException;

/**
 * An annotation to apply guardrails to the input of the model.
 * <p>
 * Guardrails can only be used with AI Services.
 * <p>
 * An input guardrail is a rule that is applied to the input of the model (basically the user message) to ensure that
 * the input is safe and meets the expectations of the model.
 * It does not replace moderation model, but it can be used to add additional checks.
 * <p>
 * Unlike for output guardrails, the input guardrails do not support retry or reprompt.
 * The failure is passed directly to the caller, wrapped into a {@link GuardrailException}
 * <p>
 * If the annotation is present on a class, the guardrails will be applied to all the methods of the class.
 * <p>
 * When several guardrails are applied, the order of the guardrails is important, as the guardrails are applied in the
 * order they are listed.
 */
@Retention(RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
public @interface InputGuardrails {

    /**
     * The ordered list of guardrails to apply to the input of the model.
     * The passed classes must be implementation of {@link InputGuardrail}.
     * The order of the classes is important as the guardrails are applied in the order they are listed.
     * Note that guardrails cannot be present twice in the list.
     */
    Class<? extends InputGuardrail>[] value();

}
