package io.quarkiverse.langchain4j.testing.evaluation;

/**
 * The strategy returned by {@link EvaluationStrategy#toAdvisory(EvaluationStrategy)}, kept as a named class so the
 * wrapped strategy can still be told apart, for example in logs.
 *
 * @param <T> the type of the output
 */
final class AdvisoryStrategy<T> implements EvaluationStrategy<T> {

    private final EvaluationStrategy<T> delegate;

    AdvisoryStrategy(EvaluationStrategy<T> delegate) {
        this.delegate = delegate;
    }

    EvaluationStrategy<T> delegate() {
        return delegate;
    }

    @Override
    public EvaluationResult evaluate(EvaluationSample<T> sample, T output) {
        return delegate.evaluate(sample, output);
    }

    @Override
    public boolean isAdvisory() {
        return true;
    }

    @Override
    public String toString() {
        return "advisory(" + delegate + ")";
    }
}
