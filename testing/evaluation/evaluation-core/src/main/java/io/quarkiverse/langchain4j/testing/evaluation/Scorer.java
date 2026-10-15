package io.quarkiverse.langchain4j.testing.evaluation;

import java.io.Closeable;
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
                                    io.quarkiverse.langchain4j.testing.evaluation.EvaluationResult strategyResult = strategy
                                            .evaluate(sample, response);
                                    EvaluationResult<T> evaluation = EvaluationResult.fromCompletedEvaluation(
                                            sample, response, strategyResult);
                                    LOG.infof(
                                            "Evaluation of sample `%s` with strategy `%s`: %s (score: %.2f)",
                                            sample.name(),
                                            strategy.getClass().getSimpleName(),
                                            evaluation.passed() ? "OK" : "KO",
                                            evaluation.score());
                                    evaluations.add(new OrderedEvaluationResult(currentIndex, currentTrial, evaluation));
                                }
                            } catch (Throwable e) {
                                LOG.errorf(e, "Failed to evaluate sample `%s`", sample.name());
                                evaluations.add(
                                        new OrderedEvaluationResult(
                                                currentIndex, currentTrial,
                                                EvaluationResult.fromEvaluationThrowable(sample, e)));
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
        return new EvaluationReport<>(orderedEvalutions, trials);
    }

    public void close() {
        executor.shutdown();
    }

    public record EvaluationResult<T>(
            EvaluationSample<T> sample,
            T result,
            Throwable thrown,
            boolean passed,
            double score,
            String explanation,
            java.util.Map<String, Object> metadata) {

        public static <T> EvaluationResult<T> fromCompletedEvaluation(
                EvaluationSample<T> sample,
                T result,
                io.quarkiverse.langchain4j.testing.evaluation.EvaluationResult evaluationResult) {
            return new EvaluationResult<>(
                    sample,
                    result,
                    null,
                    evaluationResult.passed(),
                    evaluationResult.score(),
                    evaluationResult.explanation(),
                    evaluationResult.metadata());
        }

        public static <T> EvaluationResult<T> fromEvaluationThrowable(
                EvaluationSample<T> sample, Throwable thrown) {
            return new EvaluationResult<>(
                    sample,
                    null,
                    thrown,
                    false,
                    0.0,
                    thrown.getMessage(),
                    java.util.Map.of());
        }
    }

    private <T> T execute(EvaluationSample<T> sample, Function<Parameters, T> function) {
        try {
            return function.apply(sample.parameters());
        } catch (Exception e) {
            throw new AssertionError("Failed to execute sample " + sample, e);
        }
    }

    private record OrderedEvaluationResult<T>(int index, int trial, EvaluationResult<T> evaluation) {
    }
}
