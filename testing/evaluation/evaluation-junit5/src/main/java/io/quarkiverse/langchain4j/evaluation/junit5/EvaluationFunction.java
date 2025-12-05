package io.quarkiverse.langchain4j.evaluation.junit5;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an evaluation function provider, or references an evaluation function by name.
 * <p>
 * This annotation can be used in two contexts:
 * </p>
 * <h3>1. On methods - Define reusable evaluation functions</h3>
 *
 * <pre>{@code
 * @EvaluationFunction
 * TestFunction<String> chatbotGreeting() {
 *     return params -> chatbot.chat(params.get(0));
 * }
 * }</pre>
 * <p>
 * The method name becomes the function identifier (e.g., "chatbotGreeting").
 * Alternatively, specify a custom name:
 * </p>
 *
 * <pre>{@code
 * @EvaluationFunction("greeting")
 * TestFunction<String> myFunction() {
 *     return params -> chatbot.chat(params.get(0));
 * }
 * }</pre>
 *
 * <h3>2. On parameters - Inject evaluation functions into tests</h3>
 *
 * <pre>{@code
 * @Test
 * void testGreeting(
 *         @SampleLocation("samples.yaml") Samples<String> samples,
 *         @EvaluationFunction("chatbotGreeting") Function<Parameters, String> function,
 *         Scorer scorer) {
 *     var report = scorer.evaluate(samples, function);
 *     assertThat(report).hasScoreGreaterThan(85.0);
 * }
 * }</pre>
 *
 * <h3>3. In @EvaluationTest - Reference by name</h3>
 *
 * <pre>{@code
 * @EvaluationTest(samples = @SampleLocation("samples.yaml"), function = "chatbotGreeting", minScore = 85.0)
 * void smokeTest() {
 *     // Automatic evaluation
 * }
 * }</pre>
 *
 * @see EvaluationTest
 */
@Target({ ElementType.METHOD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface EvaluationFunction {

    /**
     * The name of the evaluation function.
     * <p>
     * When used on a method: optional custom name (defaults to method name).
     * When used on a parameter: required function name to inject.
     * When used in @EvaluationTest: required function name to use.
     * </p>
     *
     * @return the function name
     */
    String value() default "";
}
