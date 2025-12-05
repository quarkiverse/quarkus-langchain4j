package io.quarkiverse.langchain4j.evaluation.junit5;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Declarative annotation for automatic evaluation tests.
 * <p>
 * This annotation provides a declarative way to run evaluations with automatic
 * score validation. The test method must be void and all configuration is done
 * via annotation parameters.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@TestTemplate
@ExtendWith({ EvaluationExtension.class, EvaluationTestExtension.class })
public @interface EvaluationTest {

    /**
     * Path to the samples file to evaluate.
     * <p>
     * Required parameter. The path is resolved using SampleLoaderResolver.
     * </p>
     *
     * @return the samples file path
     */
    String samples();

    /**
     * Name of the evaluation function to use.
     * <p>
     * References a method annotated with @EvaluationFunction in the same test class.
     * </p>
     *
     * @return the evaluation function name
     */
    String function();

    /**
     * Minimum acceptable score (0-100) for the test to pass.
     * <p>
     * If the evaluation score is below this threshold, the test will fail
     * with a detailed message showing which evaluations failed.
     * </p>
     *
     * @return minimum score threshold
     */
    double minScore() default 0.0;

    /**
     * Whether to fail fast on the first evaluation failure.
     * <p>
     * When true, evaluation stops at the first failed sample.
     * When false (default), all samples are evaluated before reporting results.
     * </p>
     *
     * @return true to fail fast
     */
    boolean failFast() default false;

    /**
     * Tags to filter samples (empty = all samples).
     * <p>
     * Only samples with at least one of these tags will be evaluated.
     * If empty, all samples are evaluated.
     * </p>
     *
     * @return array of tags to filter by
     */
    String[] tags() default {};

    /**
     * Report formats to generate (default: none).
     * <p>
     * Supported formats: "markdown", "json", "html".
     * Reports will be generated in the test output directory.
     * </p>
     *
     * @return array of report format identifiers
     */
    String[] reportFormats() default {};

    /**
     * Output directory for generated reports (default: "target/evaluation-reports").
     * <p>
     * Directory path relative to the project root.
     * </p>
     *
     * @return output directory path
     */
    String reportOutputDir() default "target/evaluation-reports";

    /**
     * Evaluation strategy to use.
     * <p>
     * The strategy class must implement {@link io.quarkiverse.langchain4j.testing.evaluation.EvaluationStrategy}
     * and have either a no-arg constructor or be a CDI bean.
     * </p>
     * <p>
     * If not specified, defaults to {@link ExactMatchStrategy} which performs exact equality comparison.
     * </p>
     *
     * @return strategy class to use
     */
    Class<? extends io.quarkiverse.langchain4j.testing.evaluation.EvaluationStrategy<?>> strategy() default ExactMatchStrategy.class;
}
