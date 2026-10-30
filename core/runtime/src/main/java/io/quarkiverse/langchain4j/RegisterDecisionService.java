package io.quarkiverse.langchain4j;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.model.decision.DecisionModel;

/**
 * Makes a CDI bean of a Decision Service: an interface whose methods are each implemented by a single call to a
 * {@link DecisionModel}, the questions coming from their {@code @Decide} annotations and the answers being mapped to
 * their return types, as with {@code dev.langchain4j.service.decision.DecisionServices}.
 * <p>
 * The thresholds of the {@code boolean} answers come from a {@code ThresholdProvider} bean when there is one, or else
 * from the configuration {@code quarkus.langchain4j.decision-service."<interface simple name>".threshold} and
 * {@code quarkus.langchain4j.decision-service."<interface simple name>".threshold-by-model."<model name>"}.
 */
@Retention(RUNTIME)
@Target(TYPE)
public @interface RegisterDecisionService {

    /**
     * The name of the {@link DecisionModel} bean to use, configured like the other named models, for example
     * {@code quarkus.langchain4j.somename.decision-model.provider=openai}. The default decision model when absent.
     */
    String modelName() default "<default>";

    /**
     * The CDI scope of the bean of the Decision Service. {@code ApplicationScoped} by default, since a Decision Service
     * keeps no state.
     */
    Class<? extends Annotation> scope() default ApplicationScoped.class;
}
