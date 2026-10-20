package io.quarkiverse.langchain4j.mockapi.internal.model;

import io.quarkiverse.langchain4j.mockapi.Usage;

public record UsageImpl(int inputTokens, int outputTokens, Integer cacheReadTokens, Integer cacheCreationTokens)
        implements
            Usage {
}
