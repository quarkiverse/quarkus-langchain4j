package io.quarkiverse.langchain4j.guardrails;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * An annotation to apply guardrails to the output of the model.
 * <p>
 * Guardrails can only be used with AI Services and only on method that do not stream the response.
 * <p>
 * A guardrail is a rule that is applied to the output of the model to ensure that the output is safe and meets the
 * expectations.
 * When a validation fails, the guardrail throws a
 * {@link io.quarkiverse.langchain4j.guardrails.OutputGuardrail.ValidationException}.
 * The exception can indicate whether the request should be retried and provide a {@code reprompt} message.
 * <p>
 * In the case of reprompting, the reprompt message is added to the LLM context and the request is retried.
 *
 * <p>
 * If the annotation is present on a class, the guardrails will be applied to all the methods of the class.
 * <p>
 * When several guardrails are applied, the order of the guardrails is important. Note that in case of retry or reprompt,
 * all the guardrails will be re-applied to the new response.
 */
@Retention(RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
public @interface OutputGuardrails {

    /**
     * The ordered list of guardrails to apply to the output of the model.
     * The passed classes must be implementation of {@link OutputGuardrail}.
     * The order of the classes is important as the guardrails are applied in the order they are listed.
     * Note that guardrails cannot be present twice in the list.
     */
    Class<? extends OutputGuardrail>[] value();

}
