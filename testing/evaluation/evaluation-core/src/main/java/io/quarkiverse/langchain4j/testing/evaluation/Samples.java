package io.quarkiverse.langchain4j.testing.evaluation;

import java.util.AbstractList;
import java.util.Arrays;
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
     * @param samples the samples, if {@code null} an empty set of samples is created.
     */
    public Samples(List<EvaluationSample<T>> samples) {
        this.samples = (samples != null) ? samples : List.of();
    }

    /**
     * Create a new set of samples.
     *
     * @param samples the samples, if {@code null} an empty set of samples is created.
     */
    @SafeVarargs
    public Samples(EvaluationSample<T>... samples) {
        this.samples = (samples != null) ? List.of(samples) : List.of();
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
     * If no samples match the specified tags, an empty Samples instance is returned.
     * </p>
     *
     * @param tags the tags to filter by
     * @return a new Samples instance with filtered samples
     */
    public Samples<T> filterByTags(String... tags) {
        if ((tags == null) || (tags.length == 0)) {
            return this;
        }

        List<String> tagList = Arrays.asList(tags);
        List<EvaluationSample<T>> filtered = samples.stream()
                .filter(sample -> sample.tags().stream().anyMatch(tagList::contains))
                .toList();

        return new Samples<>(filtered);
    }
}
