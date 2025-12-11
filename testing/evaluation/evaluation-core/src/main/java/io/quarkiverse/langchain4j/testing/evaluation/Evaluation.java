package io.quarkiverse.langchain4j.testing.evaluation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

/**
 * Fluent API for creating and executing evaluations.
 * <p>
 * This class provides a builder-based API for configuring and running evaluations
 * with automatic resource management.
 * </p>
 *
 * <h2>Basic Usage</h2>
 *
 * <pre>{@code
 * var report = Evaluation.builder()
 *         .withSamples("samples.yaml")
 *         .evaluate(params -> myService.process(params.get(0)))
 *         .using(new SemanticSimilarityStrategy(0.9))
 *         .run();
 * }</pre>
 *
 * <h2>Advanced Usage</h2>
 *
 * <pre>{@code
 * var report = Evaluation.builder()
 *         .withSamples("samples.yaml")
 *         .withConcurrency(4)
 *         .withTags("smoke-test", "regression")
 *         .evaluate(params -> myService.process(params.get(0)))
 *         .using(new SemanticSimilarityStrategy(0.9))
 *         .using(new AiJudgeStrategy(judgeModel))
 *         .runAndSave(Paths.get("reports/"));
 * }</pre>
 *
 * <h2>Deferred Execution</h2>
 *
 * <pre>{@code
 * // Build a reusable runner
 * EvaluationRunner<String> runner = Evaluation.<String> builder()
 *         .withSamples("samples.yaml")
 *         .evaluate(params -> myService.process(params.get(0)))
 *         .using(new SemanticSimilarityStrategy(0.9))
 *         .build();
 *
 * // Execute multiple times
 * EvaluationReport<String> report1 = runner.run();
 * // ... modify service configuration ...
 * EvaluationReport<String> report2 = runner.run();
 * }</pre>
 *
 * @see Builder
 * @see ExecutionBuilder
 * @see EvaluationRunner
 */
public final class Evaluation {

    private static final Logger LOG = Logger.getLogger(Evaluation.class);

    private Evaluation() {
        // Prevent instantiation - use builder() instead
    }

    /**
     * Create a new evaluation builder.
     *
     * @param <T> the type of the expected output
     * @return a new builder instance
     */
    public static <T> Builder<T> builder() {
        return new BuilderImpl<>();
    }

    /**
     * Builder interface for configuring an evaluation.
     *
     * @param <T> the type of the expected output
     */
    public interface Builder<T> {
        /**
         * Set the samples to evaluate by loading from a file.
         * The file format is auto-detected from the extension (currently supports .yaml and .yml).
         *
         * @param source path to the samples file
         * @return this builder
         */
        Builder<T> withSamples(String source);

        /**
         * Set the samples to evaluate.
         *
         * @param samples the samples to evaluate
         * @return this builder
         */
        Builder<T> withSamples(Samples<T> samples);

        /**
         * Set the concurrency level for parallel evaluation.
         *
         * @param concurrency the number of threads to use (default: 1)
         * @return this builder
         */
        Builder<T> withConcurrency(int concurrency);

        /**
         * Filter samples by tags. Only samples matching at least one of the specified tags will be evaluated.
         *
         * @param tags the tags to filter by
         * @return this builder
         */
        Builder<T> withTags(String... tags);

        /**
         * Set the function to evaluate.
         * This is the function that will be called with the sample parameters to produce the actual output.
         *
         * @param function the function to evaluate
         * @return an execution builder for configuring evaluation strategies
         */
        ExecutionBuilder<T> evaluate(Function<Parameters, T> function);
    }

    /**
     * Builder interface for configuring evaluation strategies and executing the evaluation.
     * <p>
     * This interface extends {@link EvaluationRunner} to provide both immediate execution
     * via run() methods and deferred execution via build().
     * </p>
     *
     * @param <T> the type of the expected output
     */
    public interface ExecutionBuilder<T> extends EvaluationRunner<T> {
        /**
         * Add an evaluation strategy.
         * Multiple strategies can be added - each will be applied to every sample.
         *
         * @param strategy the evaluation strategy to use
         * @return this builder
         */
        ExecutionBuilder<T> using(EvaluationStrategy<T> strategy);

        /**
         * Build a reusable {@link EvaluationRunner} with the configured settings.
         * <p>
         * This allows for deferred execution - the runner can be created once and
         * executed multiple times, or passed around as a reusable component.
         * </p>
         *
         * <h3>Example: Deferred Execution</h3>
         *
         * <pre>{@code
         * EvaluationRunner<String> runner = Evaluation.<String> builder()
         *         .withSamples("samples.yaml")
         *         .evaluate(params -> service.process(params.get(0)))
         *         .using(new SemanticSimilarityStrategy())
         *         .build();
         *
         * // Execute later
         * EvaluationReport<String> report = runner.run();
         * }</pre>
         *
         * <h3>Example: Reusable Runner</h3>
         *
         * <pre>{@code
         * try (EvaluationRunner<String> runner = builder.build()) {
         *     EvaluationReport<String> report1 = runner.run();
         *     // ... modify service configuration ...
         *     EvaluationReport<String> report2 = runner.run();
         * }
         * }</pre>
         *
         * @return a reusable evaluation runner
         */
        EvaluationRunner<T> build();
    }

    /**
     * Implementation of the Builder interface.
     *
     * @param <T> the type of the expected output
     */
    private static class BuilderImpl<T> implements Builder<T>, ExecutionBuilder<T> {

        private Samples<?> samples;
        private int concurrency = 1;
        private List<String> tags = new ArrayList<>();
        private Function<Parameters, T> function;
        private final List<EvaluationStrategy<T>> strategies = new ArrayList<>();

        @Override
        public Builder<T> withSamples(String source) {
            if (source == null || source.isBlank()) {
                throw new IllegalArgumentException("Source must not be null or blank");
            }
            this.samples = YamlLoader.load(source);
            return this;
        }

        @Override
        public Builder<T> withSamples(Samples<T> samples) {
            if (samples == null) {
                throw new IllegalArgumentException("Samples must not be null");
            }
            this.samples = samples;
            return this;
        }

        @Override
        public Builder<T> withConcurrency(int concurrency) {
            if (concurrency < 1) {
                throw new IllegalArgumentException("Concurrency must be at least 1");
            }
            this.concurrency = concurrency;
            return this;
        }

        @Override
        public Builder<T> withTags(String... tags) {
            if (tags == null) {
                throw new IllegalArgumentException("Tags must not be null");
            }
            this.tags = Arrays.asList(tags);
            return this;
        }

        @Override
        public ExecutionBuilder<T> evaluate(Function<Parameters, T> function) {
            if (function == null) {
                throw new IllegalArgumentException("Function must not be null");
            }
            this.function = function;
            return this;
        }

        @Override
        public ExecutionBuilder<T> using(EvaluationStrategy<T> strategy) {
            if (strategy == null) {
                throw new IllegalArgumentException("Strategy must not be null");
            }
            this.strategies.add(strategy);
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public EvaluationReport<T> run() {
            validate();

            List<EvaluationSample<?>> filteredSamples = filterSamplesByTags();
            // Handle empty filtered samples - return empty report
            if (filteredSamples.isEmpty()) {
                LOG.warnf("No samples to evaluate after filtering");
                return new EvaluationReport<>(List.of());
            }

            Samples<T> samplesToEvaluate = (Samples<T>) new Samples(filteredSamples);

            LOG.infof("Starting evaluation with %d samples, %d strategies, concurrency: %d",
                    samplesToEvaluate.size(), strategies.size(), concurrency);

            try (Scorer scorer = new Scorer(concurrency)) {
                @SuppressWarnings("unchecked")
                EvaluationStrategy<T>[] strategiesArray = strategies.toArray(new EvaluationStrategy[0]);
                EvaluationReport<T> report = scorer.evaluate(samplesToEvaluate, function, strategiesArray);

                LOG.infof("Evaluation completed: score = %.2f%%", report.score());
                return report;
            }
        }

        @Override
        public EvaluationRunner<T> build() {
            validate();
            // Create a snapshot of current configuration for the runner
            return new EvaluationRunnerImpl<>(
                    samples,
                    concurrency,
                    new ArrayList<>(tags),
                    function,
                    new ArrayList<>(strategies));
        }

        /**
         * Validate that all required configuration is set.
         */
        private void validate() {
            if (samples == null) {
                throw new IllegalStateException("Samples must be set using withSamples()");
            }
            if (function == null) {
                throw new IllegalStateException("Function must be set using evaluate()");
            }
            if (strategies.isEmpty()) {
                throw new IllegalStateException("At least one strategy must be set using using()");
            }
        }

        /**
         * Filter samples by tags if tags are specified.
         * Returns a list (may be empty) of filtered samples.
         * <p>
         * If tags are not specified, returns all samples.
         */
        private List<EvaluationSample<?>> filterSamplesByTags() {
            if (tags.isEmpty()) {
                return new ArrayList<>(samples);
            }

            List<EvaluationSample<?>> filteredSamples = samples.stream()
                    .filter(sample -> sample.tags().stream().anyMatch(tags::contains))
                    .collect(Collectors.toList());

            if (filteredSamples.isEmpty()) {
                LOG.warnf("No samples match the specified tags: %s", tags);
            } else {
                LOG.infof("Filtered %d samples by tags: %s", filteredSamples.size(), tags);
            }

            return filteredSamples;
        }
    }

    /**
     * Reusable implementation of {@link EvaluationRunner}.
     * <p>
     * This class encapsulates the evaluation configuration and can be executed multiple times.
     * It does not manage resources internally - the Scorer is created and closed per execution.
     * </p>
     *
     * @param <T> the type of the expected output
     */
    private static class EvaluationRunnerImpl<T> implements EvaluationRunner<T> {

        private final Samples<?> samples;
        private final int concurrency;
        private final List<String> tags;
        private final Function<Parameters, T> function;
        private final List<EvaluationStrategy<T>> strategies;

        public EvaluationRunnerImpl(
                Samples<?> samples,
                int concurrency,
                List<String> tags,
                Function<Parameters, T> function,
                List<EvaluationStrategy<T>> strategies) {
            this.samples = samples;
            this.concurrency = concurrency;
            this.tags = tags;
            this.function = function;
            this.strategies = strategies;
        }

        @Override
        @SuppressWarnings("unchecked")
        public EvaluationReport<T> run() {
            List<EvaluationSample<?>> filteredSamples = filterSamplesByTags();

            // Handle empty filtered samples - return empty report
            if (filteredSamples.isEmpty()) {
                LOG.warnf("No samples to evaluate after filtering");
                return new EvaluationReport<>(List.of());
            }

            Samples<T> samplesToEvaluate = (Samples<T>) new Samples(filteredSamples);

            LOG.infof("Starting evaluation with %d samples, %d strategies, concurrency: %d",
                    samplesToEvaluate.size(), strategies.size(), concurrency);

            try (Scorer scorer = new Scorer(concurrency)) {
                @SuppressWarnings("unchecked")
                EvaluationStrategy<T>[] strategiesArray = strategies.toArray(new EvaluationStrategy[0]);
                EvaluationReport<T> report = scorer.evaluate(samplesToEvaluate, function, strategiesArray);

                LOG.infof("Evaluation completed: score = %.2f%%", report.score());
                return report;
            }
        }

        /**
         * Filter samples by tags if tags are specified.
         * Returns a list (may be empty) of filtered samples.
         */
        private List<EvaluationSample<?>> filterSamplesByTags() {
            if (tags.isEmpty()) {
                return new ArrayList<>(samples);
            }

            List<EvaluationSample<?>> filteredSamples = samples.stream()
                    .filter(sample -> sample.tags().stream().anyMatch(tags::contains))
                    .collect(Collectors.toList());

            if (filteredSamples.isEmpty()) {
                LOG.warnf("No samples match the specified tags: %s", tags);
            } else {
                LOG.infof("Filtered %d samples by tags: %s", filteredSamples.size(), tags);
            }

            return filteredSamples;
        }

        @Override
        public void close() {
            // No persistent resources to clean up
            // Scorer is created and closed per run() invocation
        }
    }
}
