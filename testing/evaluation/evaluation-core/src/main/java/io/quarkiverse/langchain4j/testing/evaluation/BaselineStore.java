package io.quarkiverse.langchain4j.testing.evaluation;

import java.util.Optional;

import io.quarkiverse.langchain4j.testing.evaluation.review.Comparison;

/**
 * Where the result of a previous run of an evaluation is kept, so that the current run can be compared with it.
 * <p>
 * Each evaluation is identified by an id chosen by the test, for instance the test method name. The
 * {@link FileBaselineStore} keeps one baseline summary per id in a directory meant to be committed with the tests.
 * </p>
 */
public interface BaselineStore {

    /**
     * Load the baseline of an evaluation.
     *
     * @param id the evaluation id, must not be {@code null}
     * @return the snapshot of the previous run, or empty if there is none
     */
    Optional<EvaluationSnapshot> load(String id);

    /**
     * Make the given report the baseline of an evaluation, replacing any previous one.
     *
     * @param id the evaluation id, must not be {@code null}
     * @param report the report of the current run, must not be {@code null}
     */
    void save(String id, EvaluationReport<?> report);

    /**
     * Keep the report of the current run where it can be reviewed and accepted as the next baseline, without
     * changing the baseline itself. The assertion calls this on every run, so accepting is always possible after
     * the fact.
     *
     * @param id the evaluation id, must not be {@code null}
     * @param report the report of the current run, must not be {@code null}
     * @param comparison how the report compares with the baseline, must not be {@code null}
     * @return a short instruction telling the developer how to review and accept this report as the baseline
     */
    String receive(String id, EvaluationReport<?> report, Comparison comparison);
}
