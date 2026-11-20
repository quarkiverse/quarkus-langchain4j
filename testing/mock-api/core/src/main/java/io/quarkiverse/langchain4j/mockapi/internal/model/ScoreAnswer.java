package io.quarkiverse.langchain4j.mockapi.internal.model;

import java.util.List;

import io.quarkiverse.langchain4j.mockapi.DecisionReply;

public record ScoreAnswer(String name, double score, List<Level> probabilities, Double confidence)
        implements
            DecisionReply.Score {
}
