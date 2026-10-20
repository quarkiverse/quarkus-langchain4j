package io.quarkiverse.langchain4j.mockapi.internal.model;

import java.util.List;

import io.quarkiverse.langchain4j.mockapi.DecisionRequestView;

public record DecisionRequestViewImpl(String modelName, String inputText, List<Question> questions)
        implements
            DecisionRequestView {
}
