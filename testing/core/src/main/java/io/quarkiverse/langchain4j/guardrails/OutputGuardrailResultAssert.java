package io.quarkiverse.langchain4j.guardrails;

import static java.util.Objects.requireNonNull;

import java.util.Objects;
import java.util.function.Consumer;

import org.assertj.core.api.AbstractObjectAssert;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.ListAssert;

import io.quarkiverse.langchain4j.guardrails.GuardrailResult.Result;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailResult.Failure;

/**
 * Custom assertions for {@link OutputGuardrailResult}s
 * <p>
 * This follows the pattern described in https://assertj.github.io/doc/#assertj-core-custom-assertions-creation
 * </p>
 */
public class OutputGuardrailResultAssert extends AbstractObjectAssert<OutputGuardrailResultAssert, OutputGuardrailResult> {
    protected OutputGuardrailResultAssert(OutputGuardrailResult outputGuardrailResult) {
        super(outputGuardrailResult, OutputGuardrailResultAssert.class);
    }

    /**
     * Creates a new {@code OutputGuardrailResultAssert} for the provided {@code OutputGuardrailResult}.
     *
     * @param actual the {@code OutputGuardrailResult} to be asserted
     * @return an {@code OutputGuardrailResultAssert} instance for chaining further assertions
     */
    public static OutputGuardrailResultAssert assertThat(OutputGuardrailResult actual) {
        return new OutputGuardrailResultAssert(actual);
    }

    /**
     * Asserts that the actual object's {@link Result} matches the given expected result.
     * If the result does not match, an assertion error is thrown with the actual and expected values.
     *
     * @param result the expected result to compare against the actual object's result
     * @return this assertion object for method chaining
     * @throws AssertionError if the actual result does not match the expected result
     */
    public OutputGuardrailResultAssert hasResult(Result result) {
        isNotNull();

        if (!Objects.equals(actual.result(), result)) {
            throw failureWithActualExpected(actual.result(), result, "Expected result to be <%s> but was <%s>", result,
                    actual.result());
        }

        return this;
    }

    /**
     * Asserts that the actual {@code OutputGuardrailResult} represents a successful state.
     * A successful state is determined by having a {@link Result} of {@link Result#SUCCESS}
     * and being equal to {@link OutputGuardrailResult#success()}.
     *
     * @return this assertion object for method chaining
     * @throws AssertionError if the actual result is not successful as per the aforementioned criteria
     */
    public OutputGuardrailResultAssert isSuccessful() {
        isNotNull();
        hasResult(Result.SUCCESS);
        isEqualTo(OutputGuardrailResult.success());

        return this;
    }

    /**
     * Asserts that the actual {@code OutputGuardrailResult} contains failures.
     * The method validates that the object being asserted is not null and
     * that there are failures present within the result.
     *
     * @return this assertion object for method chaining
     * @throws AssertionError if the actual object is null or if the failures are empty
     */
    public OutputGuardrailResultAssert hasFailures() {
        isNotNull();
        getFailuresAssert().isNotEmpty();

        return this;
    }

    /**
     * Asserts that the actual {@code OutputGuardrailResult} contains exactly one failure with the specified message.
     * If the assertion fails, an error is thrown detailing the problem.
     *
     * @param expectedFailureMessage the expected message of the single failure
     * @return this assertion object for method chaining
     * @throws AssertionError if the actual object is null, if there are no failures,
     *         if there is more than one failure, or if the single failure
     *         does not match the specified message
     */
    public OutputGuardrailResultAssert hasSingleFailureWithMessage(String expectedFailureMessage) {
        isNotNull();

        getFailuresAssert()
                .singleElement()
                .extracting(Failure::message)
                .isEqualTo(expectedFailureMessage);

        return this;
    }

    /**
     * Asserts that the actual {@code OutputGuardrailResult} contains exactly one failure with the specified message and
     * reprompt.
     * If the assertion fails, an error is thrown detailing the problem.
     *
     * @param expectedFailureMessage the expected message of the single failure
     * @param expectedReprompt the expected reprompt
     * @return this assertion object for method chaining
     * @throws AssertionError if the actual object is null, if there are no failures,
     *         if there is more than one failure, or if the single failure
     *         does not match the specified message
     */
    public OutputGuardrailResultAssert hasSingleFailureWithMessageAndReprompt(String expectedFailureMessage,
            String expectedReprompt) {

        isNotNull();

        getFailuresAssert()
                .singleElement()
                .extracting(
                        Failure::message,
                        Failure::retry,
                        Failure::reprompt)
                .containsExactly(
                        expectedFailureMessage,
                        true,
                        expectedReprompt);

        return this;
    }

    /**
     * Asserts that the {@code OutputGuardrailResult} contains exactly one {@link Failure} and verifies
     * that this failure meets the specified requirements. The requirements are defined by the provided {@link Consumer}.
     *
     * @param requirements a {@link Consumer} that defines the assertions to be applied to the single failure.
     *        Must not be {@code null}.
     * @return this assertion object for method chaining.
     * @throws NullPointerException if the {@code requirements} is {@code null}.
     * @throws AssertionError if the actual object is {@code null}, if there are no failures, if there is more than
     *         one failure, or if the single failure does not satisfy the specified requirements.
     * @see #satisfies(Consumer[])
     */
    public OutputGuardrailResultAssert assertSingleFailureSatisfies(Consumer<? super Failure> requirements) {
        isNotNull();
        requireNonNull(requirements, "The Consumer<T> expressing the assertions requirements must not be null");

        getFailuresAssert()
                .singleElement()
                .satisfies(requirements);

        return this;
    }

    private ListAssert<Failure> getFailuresAssert() {
        return Assertions.assertThat(actual.failures())
                .isNotNull()
                .asInstanceOf(InstanceOfAssertFactories.list(Failure.class));
    }
}
