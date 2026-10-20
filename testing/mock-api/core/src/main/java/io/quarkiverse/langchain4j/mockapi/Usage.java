package io.quarkiverse.langchain4j.mockapi;

import io.quarkiverse.langchain4j.mockapi.internal.model.UsageImpl;

public interface Usage {

    static Usage of(int inputTokens, int outputTokens) {
        return new UsageImpl(inputTokens, outputTokens, null, null);
    }

    int inputTokens();

    int outputTokens();

    Integer cacheReadTokens();

    Integer cacheCreationTokens();
}
