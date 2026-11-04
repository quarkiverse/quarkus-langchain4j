package io.quarkiverse.langchain4j.testing.evaluation.review;

import java.nio.file.Path;
import java.util.List;

/**
 * A pending evaluation result found on disk, ready to be accepted or rejected.
 *
 * @param pendingFile the pending file
 * @param moduleDir the module directory that holds the build output the pending file lives in
 * @param id the evaluation id
 * @param status how the result relates to the committed baseline
 * @param comparison the comparison recorded in the pending file
 * @param baselineFile the baseline file accepting would write
 * @param details the evaluations of each sample, in report order
 * @param summary the summary that accepting would write
 */
public record PendingResult(Path pendingFile, Path moduleDir, String id, ResultStatus status, Comparison comparison,
        Path baselineFile, List<SampleDetail> details, BaselineSummary summary) {

    public PendingResult {
        details = List.copyOf(details);
    }

    /**
     * What one sample received in the pending run.
     *
     * @param name the sample name
     * @param expectedOutput the expected output of the first evaluation, or {@code null} when there is none
     * @param responses one entry per evaluation of the sample, in report order
     */
    public record SampleDetail(String name, String expectedOutput, List<Response> responses) {

        public SampleDetail {
            responses = List.copyOf(responses);
        }
    }

    /**
     * One evaluation of a sample.
     *
     * @param output the actual output, or {@code null} when the sample could not be executed
     * @param passed whether the evaluation passed
     * @param advisory whether the evaluation is advisory
     * @param trial the execution of the sample that was evaluated, from 1, or 0 when the report does not say
     * @param strategy the name of the strategy that evaluated it, or {@code null} when the sample could not be executed
     *        or the report does not say
     * @param explanation why the strategy decided as it did, or {@code null} when it gave no reason
     * @param error what was thrown by the sample or the strategy, or {@code null} when nothing was
     */
    public record Response(String output, boolean passed, boolean advisory, int trial, String strategy,
            String explanation, String error) {

        /**
         * An evaluation without trial, strategy or reason.
         *
         * @param output the actual output
         * @param passed whether the evaluation passed
         * @param advisory whether the evaluation is advisory
         */
        public Response(String output, boolean passed, boolean advisory) {
            this(output, passed, advisory, 0, null, null, null);
        }
    }
}
