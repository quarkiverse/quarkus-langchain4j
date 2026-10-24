package io.quarkiverse.langchain4j.mockapi.internal.model;

import io.quarkiverse.langchain4j.mockapi.DecisionReply;

public record PredicateAnswer(String name, double probability) implements DecisionReply.Predicate {
}
