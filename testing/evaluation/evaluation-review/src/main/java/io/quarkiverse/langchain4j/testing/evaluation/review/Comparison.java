package io.quarkiverse.langchain4j.testing.evaluation.review;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * The comparison of a received run with its baseline, as recorded in the pending file.
 *
 * @param status how the run relates to the baseline
 * @param tolerance the number of points the mean score may drop without failing
 * @param meanDifference the mean of the paired differences, in points, if there was a baseline to compare with
 * @param standardError the standard error of the mean difference
 * @param confidenceLow the lower bound of the 95% confidence interval of the mean difference
 * @param confidenceHigh the upper bound of the 95% confidence interval of the mean difference
 * @param samples one entry per paired sample, including unchanged ones
 * @param added the names of the samples only in the received run
 * @param removed the names of the samples only in the baseline
 * @param incompatibility why the runs cannot be compared, if they cannot
 * @param advisoryDifference the mean difference of the advisory sample scores, in points
 */
public record Comparison(ResultStatus status, double tolerance, OptionalDouble meanDifference,
        OptionalDouble standardError, OptionalDouble confidenceLow, OptionalDouble confidenceHigh,
        List<SampleChange> samples, List<String> added, List<String> removed, Optional<String> incompatibility,
        OptionalDouble advisoryDifference) {

    public Comparison {
        samples = List.copyOf(samples);
        added = List.copyOf(added);
        removed = List.copyOf(removed);
    }
}
