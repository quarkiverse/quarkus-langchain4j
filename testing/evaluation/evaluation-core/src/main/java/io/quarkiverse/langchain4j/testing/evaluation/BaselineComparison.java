package io.quarkiverse.langchain4j.testing.evaluation;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The comparison of a report with the baseline of a previous run, paired per sample.
 * <p>
 * Each sample present in both runs contributes the difference between its current and its baseline score, in
 * points. Pairing cancels the difficulty of the samples, so the mean difference is a much tighter estimate of
 * what changed than the difference of the two global scores. Only blocking evaluations are paired; advisory
 * evaluations are summarised separately by {@link #advisoryDifference()}.
 * </p>
 *
 * @param differences one entry per sample present in both runs, in report order
 * @param added the names of the samples present in the current run only
 * @param removed the names of the samples present in the baseline only
 * @param advisoryDifference the mean difference of the advisory sample scores, in points, or 0.0 if there are none
 * @param incompatibility why the runs cannot be compared, for instance because they used a different number of
 *        trials; empty when they can. When present, {@code differences}, {@code added} and {@code removed} are empty.
 */
public record BaselineComparison(List<SampleDifference> differences, List<String> added, List<String> removed,
        double advisoryDifference, Optional<String> incompatibility) {

    /**
     * z value of a two-sided 95% confidence interval under the normal approximation.
     */
    private static final double Z_95 = 1.96;

    public BaselineComparison {
        differences = List.copyOf(differences);
        added = List.copyOf(added);
        removed = List.copyOf(removed);
    }

    /**
     * The score of one sample in both runs.
     *
     * @param name the sample name
     * @param baselineScore the score in the baseline, between 0.0 and 100.0
     * @param currentScore the score in the current run, between 0.0 and 100.0
     */
    public record SampleDifference(String name, double baselineScore, double currentScore) {

        /**
         * @return the current score minus the baseline score, in points
         */
        public double difference() {
            return currentScore - baselineScore;
        }
    }

    /**
     * @return the mean of the paired differences, in points; positive means the current run is better
     */
    public double meanDifference() {
        return Arrays.stream(differenceValues()).average().orElse(0.0);
    }

    /**
     * The standard error of the mean difference, in points, 0.0 with fewer than two paired samples.
     *
     * @return the standard error
     */
    public double standardError() {
        int n = differences.size();
        if (n < 2) {
            return 0.0;
        }
        double mean = meanDifference();
        double variance = Arrays.stream(differenceValues()).map(d -> (d - mean) * (d - mean)).sum() / (n - 1);
        return Math.sqrt(variance / n);
    }

    /**
     * @return the 95% confidence interval of the mean difference, in points
     */
    public EvaluationReport.Interval confidenceInterval() {
        double margin = Z_95 * standardError();
        return new EvaluationReport.Interval(Math.max(-100.0, meanDifference() - margin),
                Math.min(100.0, meanDifference() + margin));
    }

    /**
     * @return the samples whose score is lower than in the baseline
     */
    public List<SampleDifference> regressions() {
        return differences.stream().filter(d -> d.difference() < 0).toList();
    }

    private double[] differenceValues() {
        return differences.stream().mapToDouble(SampleDifference::difference).toArray();
    }
}
