package io.quarkiverse.langchain4j.mockapi.internal.model;

import java.time.Duration;
import java.util.List;

import io.quarkiverse.langchain4j.mockapi.ScoringReply;

public record ScoringReplyImpl(List<Double> scores, Duration delay) implements ScoringReply {

    @Override
    public ScoringReply delay(Duration delay) {
        return new ScoringReplyImpl(scores, delay);
    }
}
