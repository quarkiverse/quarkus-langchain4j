package io.quarkiverse.langchain4j.mockapi.internal.model;

import java.time.Duration;
import java.util.List;

import io.quarkiverse.langchain4j.mockapi.DecisionReply;
import io.quarkiverse.langchain4j.mockapi.Usage;

public record DecisionReplyImpl(List<Answer> answers, Usage usage, String modelName, Duration delay) implements DecisionReply {

    @Override
    public DecisionReply usage(int in, int out) {
        return new DecisionReplyImpl(answers, Usage.of(in, out), modelName, delay);
    }

    @Override
    public DecisionReply modelName(String modelName) {
        return new DecisionReplyImpl(answers, usage, modelName, delay);
    }

    @Override
    public DecisionReply delay(Duration delay) {
        return new DecisionReplyImpl(answers, usage, modelName, delay);
    }
}
