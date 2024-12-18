package io.quarkiverse.langchain4j.testing.scorer;

import java.io.Closeable;
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
    public <T> EvaluationReport evaluate(Samples<T> samples, Function<Parameters, T> function,
            EvaluationStrategy<T>... strategies) {
        List<EvaluationResult<?>> evaluations = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(samples.size());
        for (EvaluationSample<T> sample : samples) {
            // TODO Should we handle the context somehow.
            executor.submit(() -> {
                try {
                    var response = execute(sample, function);
                    LOG.infof("Evaluating sample `%s`", sample.name());
                    for (EvaluationStrategy<T> strategy : strategies) {
                        EvaluationResult<T> evaluation = new EvaluationResult<>(sample,
                                strategy.evaluate(sample, response));
                        LOG.infof("Evaluation of sample `%s` with strategy `%s`: %s", sample.name(),
                                strategy.getClass().getSimpleName(),
                                evaluation.passed() ? "OK" : "KO");
                        evaluations.add(evaluation);
                    }
                } catch (Throwable e) {
                    LOG.errorf(e, "Failed to evaluate sample `%s`", sample.name());
                    evaluations.add(new EvaluationResult<>(sample, false));
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
        return new EvaluationReport(evaluations);
    }

    public void close() {
        executor.shutdown();
    }

    public record EvaluationResult<T>(EvaluationSample<T> sample, boolean passed) {
    }

    private <T> T execute(EvaluationSample<T> sample, Function<Parameters, T> function) {
        try {
            return function.apply(sample.parameters());
        } catch (Exception e) {
            throw new AssertionError("Failed to execute sample " + sample, e);
        }
    }

}
