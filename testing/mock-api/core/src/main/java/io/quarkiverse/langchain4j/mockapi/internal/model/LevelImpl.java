package io.quarkiverse.langchain4j.mockapi.internal.model;

import io.quarkiverse.langchain4j.mockapi.DecisionReply;

public record LevelImpl(int value, String label, double probability) implements DecisionReply.Score.Level {
}
