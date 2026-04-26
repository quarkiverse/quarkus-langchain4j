package io.quarkiverse.langchain4j.evaluation.junit5;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Annotation to enable the LangChain4J Evaluation JUnit 5 extension.
 * This is equivalent to adding the {@code @ExtendWith(EvaluationExtension.class)} annotation to a test class.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE })
@ExtendWith(EvaluationExtension.class)
public @interface Evaluate {
}
