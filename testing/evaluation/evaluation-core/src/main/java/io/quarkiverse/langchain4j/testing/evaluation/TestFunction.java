package io.quarkiverse.langchain4j.testing.evaluation;

import java.util.function.Function;

/**
 * Functional interface representing a function to be tested during evaluation.
 * <p>
 * This function takes parameters from an evaluation sample and produces an output
 * that will be compared against the expected output using an evaluation strategy.
 * </p>
 *
 * @param <T> the type of output produced by the function
 */
@FunctionalInterface
public interface TestFunction<T> extends Function<Parameters, T> {
    // TestFunction extends Function<Parameters, T> so it can be used directly
    // with Scorer.evaluate() which accepts Function<Parameters, T>
}
