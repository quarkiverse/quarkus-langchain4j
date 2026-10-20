package io.quarkiverse.langchain4j.mockapi;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import io.quarkiverse.langchain4j.mockapi.internal.model.ScoringReplyImpl;

public non-sealed interface ScoringReply extends Reply {

    static ScoringReply of(double... scores) {
        return new ScoringReplyImpl(Arrays.stream(scores).boxed().toList(), null);
    }

    /**
     * Gives every scored segment the same score.
     */
    static ScoringReply equalScores() {
        return new ScoringReplyImpl(null, null);
    }

    List<Double> scores();

    ScoringReply delay(Duration delay);
}
