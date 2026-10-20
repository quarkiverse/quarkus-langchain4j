package io.quarkiverse.langchain4j.mockapi.internal.model;

import java.util.List;

import io.quarkiverse.langchain4j.mockapi.DecisionRequestView;

public record QuestionImpl(String type, String name, String instructions, List<String> choices, List<String> levels)
        implements
            DecisionRequestView.Question {
}
