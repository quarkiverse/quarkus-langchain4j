package io.quarkiverse.langchain4j.evaluation.junit5;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import io.quarkiverse.langchain4j.testing.evaluation.EvaluationStrategy;

/**
 * Test template annotation for running the same evaluation with multiple strategies.
 * <p>
 * This annotation creates multiple test executions - one for each specified strategy.
 * Each execution will inject the corresponding strategy instance into the test method parameter.
 * </p>
 *
 * <h2>Usage Example:</h2>
 *
 * <pre>
 * {@code
 * @StrategyTest(strategies = {
 *         SemanticSimilarityStrategy.class,
 *         AiJudgeStrategy.class
 * }, minScore = 80.0)
 * void testWithMultipleStrategies(
 *         @SampleLocation("samples.yaml") Samples<String> samples,
 *         EvaluationStrategy<String> strategy, // Injected by template
 *         Scorer scorer) {
 *     var report = scorer.evaluate(
 *             samples,
 *             params -> chatbot.chat(params.get(0)),
 *             strategy);
 *
 *     assertThat(report).hasScoreGreaterThan(80.0);
 * }
 * }
 * </pre>
 *
 * <p>
 * This creates multiple test executions:
 * <ul>
 * <li>{@code testWithMultipleStrategies[SemanticSimilarityStrategy]}</li>
 * <li>{@code testWithMultipleStrategies[AiJudgeStrategy]}</li>
 * </ul>
 * </p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@TestTemplate
@ExtendWith(StrategyTestInvocationContextProvider.class)
public @interface StrategyTest {

    /**
     * Array of strategy classes to test.
     * <p>
     * Each strategy class must:
     * <ul>
     * <li>Implement {@link EvaluationStrategy}</li>
     * <li>Have a no-arg constructor, or be a CDI bean</li>
     * </ul>
     * </p>
     *
     * @return array of strategy classes
     */
    Class<? extends EvaluationStrategy<?>>[] strategies();

    /**
     * Minimum acceptable score (0-100) for the test to pass.
     * <p>
     * If specified and greater than 0, the test will automatically fail if the evaluation score
     * is below this threshold. The score is validated from
     * {@link io.quarkiverse.langchain4j.testing.evaluation.EvaluationReport}
     * fields annotated with {@link ReportConfiguration}.
     * </p>
     * <p>
     * Example usage:
     * </p>
     *
     * <pre>
     * {
     *     &#64;code
     *     class MyTest {
     *         &#64;ReportConfiguration
     *         private EvaluationReport<String> report;
     *
     *         @StrategyTest(strategies = { MyStrategy.class }, minScore = 80.0)
     *         void testWithMinScore(@SampleLocation("samples.yaml") Samples<String> samples,
     *                 EvaluationStrategy<String> strategy,
     *                 Scorer scorer) {
     *             // Report will be validated against minScore automatically
     *             report = scorer.evaluate(samples, params -> process(params), strategy);
     *         }
     *     }
     * }
     * </pre>
     *
     * @return minimum score (default: 0.0 = no validation)
     */
    double minScore() default 0.0;
}
