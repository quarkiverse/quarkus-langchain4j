package io.quarkiverse.langchain4j.mockapi.internal.model;

import io.quarkiverse.langchain4j.mockapi.DecisionReply;

public record DeclinedAnswer(String name) implements DecisionReply.Declined {
}
