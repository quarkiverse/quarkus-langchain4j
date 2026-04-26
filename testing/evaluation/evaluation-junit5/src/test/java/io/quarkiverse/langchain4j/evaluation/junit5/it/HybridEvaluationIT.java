package io.quarkiverse.langchain4j.evaluation.junit5.it;

import static io.quarkiverse.langchain4j.testing.evaluation.EvaluationAssertions.assertThat;

import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.quarkiverse.langchain4j.evaluation.junit5.EvaluationExtension;
import io.quarkiverse.langchain4j.evaluation.junit5.EvaluationFunction;
import io.quarkiverse.langchain4j.evaluation.junit5.EvaluationTest;
import io.quarkiverse.langchain4j.evaluation.junit5.SampleLocation;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationResult;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationStrategy;
import io.quarkiverse.langchain4j.testing.evaluation.Parameters;
import io.quarkiverse.langchain4j.testing.evaluation.Samples;
import io.quarkiverse.langchain4j.testing.evaluation.Scorer;
import io.quarkiverse.langchain4j.testing.evaluation.TestFunction;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@ExtendWith(EvaluationExtension.class)
class HybridEvaluationIT {

    /**
     * Simple echo function - returns the first parameter unchanged.
     * Function name is "echoFunction" (the method name).
     */
    @EvaluationFunction
    TestFunction<String> echoFunction() {
        return params -> params.<String> get(0);
    }

    /**
     * Uppercase function with custom name "uppercase".
     */
    @EvaluationFunction("uppercase")
    TestFunction<String> transformToUppercase() {
        return params -> params.<String> get(0).toUpperCase();
    }

    /**
     * Greeting function that adds "Hello, " prefix.
     */
    @EvaluationFunction
    TestFunction<String> greetingFunction() {
        return params -> "Hello, " + params.<String> get(0);
    }

    /**
     * Programmatic test using injected evaluation function.
     */
    @Test
    void testWithInjectedFunction(
            @SampleLocation("src/test/resources/assertion-test-samples.yaml") Samples<String> samples,
            @EvaluationFunction("echoFunction") Function<Parameters, String> function,
            Scorer scorer) {

        // Create evaluation strategy (exact match)
        EvaluationStrategy<String> strategy = (sample, actual) -> EvaluationResult.fromBoolean(
                sample.expectedOutput().equals(actual));

        var report = scorer.evaluate(samples, function, strategy);

        // Full control over assertions with fluent API
        assertThat(report)
                .hasScoreGreaterThanOrEqualTo(90.0)
                .hasAllPassed()
                .hasEvaluationCount(3)
                .hasPassedCount(3);
    }

    /**
     * Programmatic test with tag filtering.
     */
    @Test
    void testGreetingsOnly(
            @SampleLocation("src/test/resources/assertion-test-samples.yaml") Samples<String> samples,
            @EvaluationFunction("echoFunction") Function<Parameters, String> function,
            Scorer scorer) {

        // Filter to only greeting samples
        var greetingSamples = samples.filterByTags("greeting");

        // Create evaluation strategy (exact match)
        EvaluationStrategy<String> strategy = (sample, actual) -> EvaluationResult.fromBoolean(
                sample.expectedOutput().equals(actual));

        var report = scorer.evaluate(greetingSamples, function, strategy);

        // Verify only greeting samples were evaluated
        assertThat(report)
                .hasAllPassed()
                .hasPassedForTag("greeting")
                .hasScoreForTag("greeting", 95.0);
    }

    /**
     * Programmatic test with detailed failure analysis.
     */
    @Test
    void testWithDetailedAssertions(
            @SampleLocation("src/test/resources/assertion-test-samples.yaml") Samples<String> samples,
            @EvaluationFunction("echoFunction") Function<Parameters, String> function,
            Scorer scorer) {

        // Create evaluation strategy (exact match)
        EvaluationStrategy<String> strategy = (sample, actual) -> EvaluationResult.fromBoolean(
                sample.expectedOutput().equals(actual));

        var report = scorer.evaluate(samples, function, strategy);

        // Multiple chained assertions for detailed validation
        assertThat(report)
                .hasScoreGreaterThan(95.0)
                .hasAllPassed()
                .hasAtLeastEvaluations(2)
                .hasPassedCount(3)
                .hasFailedCount(0)
                .hasEvaluationCount(3);
    }

    /**
     * Declarative test - evaluation runs automatically.
     * Test fails if score < 90%.
     */
    @EvaluationTest(samples = "src/test/resources/assertion-test-samples.yaml", function = "echoFunction", minScore = 90.0)
    void smokeTest() {
        // Everything happens automatically:
    }

    /**
     * Declarative test with tag filtering.
     */
    @EvaluationTest(samples = "src/test/resources/assertion-test-samples.yaml", function = "echoFunction", minScore = 100.0, tags = {
            "greeting" })
    void greetingOnlyTest() {
        // Only samples tagged with "greeting" are evaluated
    }

    /**
     * Declarative test with report generation.
     */
    @EvaluationTest(samples = "src/test/resources/assertion-test-samples.yaml", function = "echoFunction", minScore = 85.0, reportFormats = {
            "markdown", "json" }, reportOutputDir = "target/evaluation-reports")
    void testWithReports() {
        // Generates markdown and json reports in target/evaluation-reports
    }
}
