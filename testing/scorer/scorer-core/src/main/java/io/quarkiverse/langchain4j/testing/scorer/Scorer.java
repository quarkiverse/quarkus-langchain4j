package io.quarkiverse.langchain4j.testing.scorer;

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

    public Scorer(int concurrency) {
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
        CountDownLatch latch = new CountDownLatch(samples.size());
        var index = 0;
        for (EvaluationSample<T> sample : samples) {
            // TODO Should we handle the context somehow.
            var currentIndex = index++;
            executor.submit(
                    () -> {
                        try {
                            var response = execute(sample, function);
                            LOG.infof("Evaluating sample `%s`", sample.name());
                            for (EvaluationStrategy<T> strategy : strategies) {
                                EvaluationResult<T> evaluation = EvaluationResult.fromCompletedEvaluation(
                                        sample, response, strategy.evaluate(sample, response));
                                LOG.infof(
                                        "Evaluation of sample `%s` with strategy `%s`: %s",
                                        sample.name(),
                                        strategy.getClass().getSimpleName(),
                                        evaluation.passed() ? "OK" : "KO");
                                evaluations.add(new OrderedEvaluationResult(currentIndex, evaluation));
                            }
                        } catch (Throwable e) {
                            LOG.errorf(e, "Failed to evaluate sample `%s`", sample.name());
                            evaluations.add(
                                    new OrderedEvaluationResult(
                                            currentIndex, EvaluationResult.fromEvaluationThrowable(sample, e)));
                        } finally {
                            latch.countDown();
                        }
                    });
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        var orderedEvalutions = evaluations.stream()
                .sorted(Comparator.comparing(OrderedEvaluationResult::index))
                .map(OrderedEvaluationResult::evaluation)
                .toList();
        return new EvaluationReport<>(orderedEvalutions);
    }

    public void close() {
        executor.shutdown();
    }

    public record EvaluationResult<T>(
            EvaluationSample<T> sample, T result, Throwable thrown, boolean passed) {
        public static <T> EvaluationResult<T> fromCompletedEvaluation(
                EvaluationSample<T> sample, T result, boolean passed) {
            return new EvaluationResult<>(sample, result, null, passed);
        }

        public static <T> EvaluationResult<T> fromEvaluationThrowable(
                EvaluationSample<T> sample, Throwable thrown) {
            return new EvaluationResult<>(sample, null, thrown, false);
        }
    }

    private <T> T execute(EvaluationSample<T> sample, Function<Parameters, T> function) {
        try {
            return function.apply(sample.parameters());
        } catch (Exception e) {
            throw new AssertionError("Failed to execute sample " + sample, e);
        }
    }

    private record OrderedEvaluationResult<T>(int index, EvaluationResult<T> evaluation) {
    }
}
