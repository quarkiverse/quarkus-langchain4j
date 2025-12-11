package io.quarkiverse.langchain4j.testing.evaluation;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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

    /**
     * Filter samples by tags.
     * <p>
     * Returns a new Samples instance containing only samples that have at least one of the specified tags.
     * </p>
     *
     * @param tags the tags to filter by
     * @return a new Samples instance with filtered samples
     * @throws IllegalArgumentException if no samples match the tags
     */
    public Samples<T> filterByTags(String... tags) {
        if (tags == null || tags.length == 0) {
            return this;
        }

        List<String> tagList = Arrays.asList(tags);
        List<EvaluationSample<T>> filtered = samples.stream()
                .filter(sample -> sample.tags().stream().anyMatch(tagList::contains))
                .collect(Collectors.toList());

        if (filtered.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("No samples found with tags: %s", Arrays.toString(tags)));
        }

        return new Samples<>(filtered);
    }
}
