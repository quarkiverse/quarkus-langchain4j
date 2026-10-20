package io.quarkiverse.langchain4j.mockapi.internal.model;

import java.util.Map;

import io.quarkiverse.langchain4j.mockapi.DecisionReply;

public record ChoiceAnswer(String name, String choice, Map<String, Double> probabilities, Double confidence)
        implements
            DecisionReply.Choice {
}
