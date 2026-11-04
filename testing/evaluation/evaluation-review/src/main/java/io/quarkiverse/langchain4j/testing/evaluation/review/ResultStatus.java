package io.quarkiverse.langchain4j.testing.evaluation.review;

/**
 * How the result of a run relates to the committed baseline.
 */
public enum ResultStatus {
    /**
     * There is no baseline yet.
     */
    NEW,
    /**
     * The run cannot be compared with the baseline, for instance because the number of trials differs.
     */
    INCOMPATIBLE,
    /**
     * The score dropped by more than the tolerance.
     */
    FAILED,
    /**
     * The test passes, but some sample scores moved or samples were added or removed.
     */
    CHANGED,
    /**
     * Nothing differs from the baseline.
     */
    UNCHANGED
}
