package io.quarkiverse.langchain4j.testing.scorer;

import java.util.AbstractList;
import java.util.List;

/**
 * A list of {@link EvaluationSample} instances.
 *
 * @param <T> the type of the expected output as all samples from the set should have the same type.
 */
public class Samples<T> extends AbstractList<EvaluationSample<T>>
        implements List<EvaluationSample<T>> {

    private final List<EvaluationSample<T>> samples;

    /**
     * Create a new set of samples.
     *
     * @param samples the samples, must not be {@code null}, must not be empty.
     */
    public Samples(List<EvaluationSample<T>> samples) {
        if (samples == null) {
            throw new IllegalArgumentException("Samples must not be null");
        }
        if (samples.isEmpty()) {
            throw new IllegalArgumentException("Samples must not be empty");
        }
        this.samples = samples;
    }

    /**
     * Create a new set of samples.
     *
     * @param samples the samples, must not be {@code null}, must not be empty.
     */
    @SafeVarargs
    public Samples(EvaluationSample<T>... samples) {
        if (samples == null) {
            throw new IllegalArgumentException("Samples must not be null");
        }
        if (samples.length == 0) {
            throw new IllegalArgumentException("Samples must not be empty");
        }
        this.samples = List.of(samples);
    }

    /**
     * Gets the sample at the given index.
     */
    @Override
    public EvaluationSample<T> get(int index) {
        return samples.get(index);
    }

    /**
     * Gets the number of samples.
     */
    @Override
    public int size() {
        return samples.size();
    }
}
