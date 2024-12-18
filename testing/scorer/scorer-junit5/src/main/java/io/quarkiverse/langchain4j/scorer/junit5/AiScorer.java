package io.quarkiverse.langchain4j.scorer.junit5;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Annotation to enable the LangChain4J Scorer JUnit 5 extension.
 * This is equivalent to adding the {@code @ExtendWith(ScorerExtension.class)} annotation to a test class.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE })
@ExtendWith(ScorerExtension.class)
public @interface AiScorer {
}
