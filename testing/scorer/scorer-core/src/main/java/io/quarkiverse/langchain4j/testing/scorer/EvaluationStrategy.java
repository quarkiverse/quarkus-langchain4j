package io.quarkiverse.langchain4j.testing.scorer;

/**
 * A strategy to evaluate the output of a model.
 *
 * @param <T> the type of the output.
 */
public interface EvaluationStrategy<T> {

    /**
     * Evaluate the output of a model.
     *
     * @param sample the sample to evaluate.
     * @param output the output of the model.
     * @return {@code true} if the output is correct, {@code false} otherwise.
     */
    boolean evaluate(EvaluationSample<T> sample, T output);

}
