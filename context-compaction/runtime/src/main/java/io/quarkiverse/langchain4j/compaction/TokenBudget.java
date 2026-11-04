package io.quarkiverse.langchain4j.compaction;

/**
 * The token budget of a conversation.
 * <p>
 * The usable window is the model context window minus the tokens reserved for the completion. Compaction is
 * triggered when the history exceeds {@code triggerRatio} of the usable window, and strategies aim to bring it under
 * {@code targetRatio} of the usable window. Keeping the target below the trigger avoids compacting on every turn.
 *
 * @param contextWindow the model context window, in tokens
 * @param reservedForCompletion tokens reserved for the model answer ({@code max_tokens})
 * @param triggerRatio fraction of the usable window above which compaction runs
 * @param targetRatio fraction of the usable window strategies aim for
 */
public record TokenBudget(int contextWindow, int reservedForCompletion, double triggerRatio, double targetRatio) {

    public TokenBudget {
        if (contextWindow <= 0) {
            throw new IllegalArgumentException("contextWindow must be positive");
        }
        if (reservedForCompletion < 0 || reservedForCompletion >= contextWindow) {
            throw new IllegalArgumentException("reservedForCompletion must be in [0, contextWindow)");
        }
        if (triggerRatio <= 0 || triggerRatio > 1) {
            throw new IllegalArgumentException("triggerRatio must be in (0, 1]");
        }
        if (targetRatio <= 0 || targetRatio > triggerRatio) {
            throw new IllegalArgumentException("targetRatio must be in (0, triggerRatio]");
        }
    }

    public static TokenBudget of(int contextWindow) {
        return new TokenBudget(contextWindow, 0, 1.0, 1.0);
    }

    /**
     * @return the number of tokens usable by the prompt
     */
    public int usable() {
        return contextWindow - reservedForCompletion;
    }

    /**
     * @return the number of tokens above which compaction runs
     */
    public int trigger() {
        return (int) (usable() * triggerRatio);
    }

    /**
     * @return the number of tokens strategies aim for
     */
    public int target() {
        return (int) (usable() * targetRatio);
    }

    public boolean exceeded(int tokens) {
        return tokens > trigger();
    }
}
