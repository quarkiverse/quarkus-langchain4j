package io.quarkiverse.langchain4j.testing.evaluation;

import java.io.Closeable;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import org.jboss.logging.Logger;

public class Scorer implements Closeable {

    private static final Logger LOG = Logger.getLogger(Scorer.class);
    private final ExecutorService executor;
    private final int trials;

    public Scorer(int concurrency) {
        this(concurrency, 1);
    }

    /**
     * @param concurrency the number of threads to use for the evaluation
     * @param trials the number of times each sample is executed and evaluated, at least 1. Running each sample
     *        several times is what makes the report's score and confidence interval meaningful for a
     *        non-deterministic function under test.
     */
    public Scorer(int concurrency, int trials) {
        if (trials < 1) {
            throw new IllegalArgumentException("Trials must be at least 1, got: " + trials);
        }
        this.trials = trials;
        if (concurrency > 1) {
            executor = Executors.newFixedThreadPool(concurrency);
        } else {
            executor = Executors.newSingleThreadExecutor();
        }
    }

    public Scorer() {
        this(1);
    }

    @SuppressWarnings({ "unchecked" })
    public <T> EvaluationReport<T> evaluate(
            Samples<T> samples, Function<Parameters, T> function, EvaluationStrategy<T>... strategies) {
        List<OrderedEvaluationResult<T>> evaluations = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(samples.size() * trials);
        var index = 0;
        for (EvaluationSample<T> sample : samples) {
            // TODO Should we handle the context somehow.
            var currentIndex = index++;
            for (int trial = 0; trial < trials; trial++) {
                var currentTrial = trial;
                executor.submit(
                        () -> {
                            try {
                                var response = execute(sample, function);
                                LOG.infof("Evaluating sample `%s`", sample.name());
                                for (EvaluationStrategy<T> strategy : strategies) {
                                    EvaluationResult<T> evaluation;
                                    try {
                                        io.quarkiverse.langchain4j.testing.evaluation.EvaluationResult strategyResult = strategy
                                                .evaluate(sample, response);
                                        evaluation = EvaluationResult.fromCompletedEvaluation(
                                                sample, response, strategyResult, strategy.isAdvisory());
                                        LOG.infof(
                                                "Evaluation of sample `%s` with strategy `%s`: %s (score: %.2f)",
                                                sample.name(),
                                                nameOf(strategy),
                                                evaluation.passed() ? "OK" : "KO",
                                                evaluation.score());
                                    } catch (Throwable e) {
                                        // The strategy itself failed, not the function under test: record it against
                                        // that strategy so an advisory strategy failing cannot block.
                                        LOG.errorf(e, "Strategy `%s` failed to evaluate sample `%s`",
                                                nameOf(strategy), sample.name());
                                        evaluation = EvaluationResult.fromEvaluationThrowable(
                                                sample, response, e, strategy.isAdvisory());
                                    }
                                    evaluations.add(new OrderedEvaluationResult(currentIndex, currentTrial,
                                            evaluation.from(currentTrial + 1, nameOf(strategy))));
                                }
                            } catch (Throwable e) {
                                LOG.errorf(e, "Failed to evaluate sample `%s`", sample.name());
                                evaluations.add(
                                        new OrderedEvaluationResult(
                                                currentIndex, currentTrial,
                                                EvaluationResult.fromEvaluationThrowable(sample, e)
                                                        .from(currentTrial + 1, null)));
                            } finally {
                                latch.countDown();
                            }
                        });
            }
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        var orderedEvalutions = evaluations.stream()
                .sorted(Comparator.comparing(OrderedEvaluationResult<T>::index)
                        .thenComparing(OrderedEvaluationResult::trial))
                .map(OrderedEvaluationResult::evaluation)
                .toList();
        return new EvaluationReport<>(orderedEvalutions, trials,
                Arrays.stream(strategies).map(Scorer::nameOf).toList());
    }

    public void close() {
        executor.shutdown();
    }

    /**
     * The evaluation of one sample execution by one strategy.
     *
     * @param advisory whether the result comes from an advisory strategy and therefore does not affect the score,
     *        see {@link EvaluationStrategy#isAdvisory()}
     * @param trial the execution of the sample this result evaluates, from 1, or 0 when unknown
     * @param strategy the name of the strategy that produced this result, or {@code null} when the sample could not be
     *        executed or the result was not produced by a {@link Scorer}
     */
    public record EvaluationResult<T>(
            EvaluationSample<T> sample,
            T result,
            Throwable thrown,
            boolean passed,
            double score,
            String explanation,
            java.util.Map<String, Object> metadata,
            boolean advisory,
            int trial,
            String strategy) {

        public EvaluationResult(
                EvaluationSample<T> sample,
                T result,
                Throwable thrown,
                boolean passed,
                double score,
                String explanation,
                java.util.Map<String, Object> metadata,
                boolean advisory) {
            this(sample, result, thrown, passed, score, explanation, metadata, advisory, 0, null);
        }

        /**
         * This result, recorded as produced by the given trial and strategy.
         *
         * @param trial the execution of the sample, from 1
         * @param strategy the name of the strategy, or {@code null} when the sample could not be executed
         * @return a copy of this result with the trial and strategy set
         */
        public EvaluationResult<T> from(int trial, String strategy) {
            return new EvaluationResult<>(sample, result, thrown, passed, score, explanation, metadata, advisory, trial,
                    strategy);
        }

        public EvaluationResult(
                EvaluationSample<T> sample,
                T result,
                Throwable thrown,
                boolean passed,
                double score,
                String explanation,
                java.util.Map<String, Object> metadata) {
            this(sample, result, thrown, passed, score, explanation, metadata, false);
        }

        public static <T> EvaluationResult<T> fromCompletedEvaluation(
                EvaluationSample<T> sample,
                T result,
                io.quarkiverse.langchain4j.testing.evaluation.EvaluationResult evaluationResult) {
            return fromCompletedEvaluation(sample, result, evaluationResult, false);
        }

        public static <T> EvaluationResult<T> fromCompletedEvaluation(
                EvaluationSample<T> sample,
                T result,
                io.quarkiverse.langchain4j.testing.evaluation.EvaluationResult evaluationResult,
                boolean advisory) {
            return new EvaluationResult<>(
                    sample,
                    result,
                    null,
                    evaluationResult.passed(),
                    evaluationResult.score(),
                    evaluationResult.explanation(),
                    evaluationResult.metadata(),
                    advisory);
        }

        public static <T> EvaluationResult<T> fromEvaluationThrowable(
                EvaluationSample<T> sample, Throwable thrown) {
            return fromEvaluationThrowable(sample, null, thrown, false);
        }

        /**
         * A failed evaluation of an output that was produced, but could not be evaluated by a strategy.
         *
         * @param result the output of the function under test, may be {@code null}
         * @param thrown what the strategy threw
         * @param advisory whether the strategy was advisory
         */
        public static <T> EvaluationResult<T> fromEvaluationThrowable(
                EvaluationSample<T> sample, T result, Throwable thrown, boolean advisory) {
            return new EvaluationResult<>(
                    sample,
                    result,
                    thrown,
                    false,
                    0.0,
                    thrown.getMessage(),
                    java.util.Map.of(),
                    advisory);
        }
    }

    private <T> T execute(EvaluationSample<T> sample, Function<Parameters, T> function) {
        try {
            return function.apply(sample.parameters());
        } catch (Exception e) {
            throw new AssertionError("Failed to execute sample " + sample, e);
        }
    }

    private static String nameOf(EvaluationStrategy<?> strategy) {
        if (strategy instanceof AdvisoryStrategy<?> advisory) {
            return nameOf(advisory.delegate()) + " (advisory)";
        }
        Class<?> type = strategy.getClass();
        if (type.isHidden()) {
            // Lambdas are hidden classes whose names change on every run.
            return "lambda";
        }
        String simpleName = type.getSimpleName();
        if (simpleName.isEmpty()) {
            // Anonymous class: use the binary name without the package, e.g. `ScorerTest$1`.
            String packageName = type.getPackageName();
            return packageName.isEmpty() ? type.getName() : type.getName().substring(packageName.length() + 1);
        }
        return simpleName;
    }

    private record OrderedEvaluationResult<T>(int index, int trial, EvaluationResult<T> evaluation) {
    }
}
