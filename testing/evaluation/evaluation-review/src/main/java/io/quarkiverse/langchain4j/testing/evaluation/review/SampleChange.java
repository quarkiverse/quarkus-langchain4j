package io.quarkiverse.langchain4j.testing.evaluation.review;

import java.util.OptionalDouble;

/**
 * The scores of one sample that is both in the baseline and in the received run. The blocking scores are the ones
 * compared with the tolerance; the advisory scores are only shown. A score is empty when the sample has no
 * evaluation of that kind.
 *
 * @param name the sample name
 * @param baseline the blocking score in the baseline, in points
 * @param received the blocking score in the received run, in points
 * @param advisoryBaseline the advisory score in the baseline, in points
 * @param advisoryReceived the advisory score in the received run, in points
 */
public record SampleChange(String name, OptionalDouble baseline, OptionalDouble received,
        OptionalDouble advisoryBaseline, OptionalDouble advisoryReceived) {

    /**
     * A sample with blocking evaluations only.
     *
     * @param name the sample name
     * @param baseline the blocking score in the baseline, in points
     * @param received the blocking score in the received run, in points
     */
    public SampleChange(String name, double baseline, double received) {
        this(name, OptionalDouble.of(baseline), OptionalDouble.of(received), OptionalDouble.empty(),
                OptionalDouble.empty());
    }

    /**
     * Tells whether a score of the sample moved.
     *
     * @return {@code true} when the blocking or the advisory score differs from the baseline
     */
    public boolean changed() {
        return !baseline.equals(received) || !advisoryBaseline.equals(advisoryReceived);
    }

    /**
     * Tells whether the blocking score dropped.
     *
     * @return {@code true} when both blocking scores are known and the received one is lower
     */
    public boolean regressed() {
        return baseline.isPresent() && received.isPresent() && received.getAsDouble() < baseline.getAsDouble();
    }
}
