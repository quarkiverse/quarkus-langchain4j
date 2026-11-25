package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.Optional;

final class DefaultAiServiceSpanCompleteContext implements AiServiceSpanCompleteContext {

    private final AiServiceSpanStartContext startContext;
    private final Optional<Object> result;

    DefaultAiServiceSpanCompleteContext(Builder builder) {
        this.startContext = builder.startContext;
        this.result = Optional.ofNullable(builder.result);
    }

    @Override
    public AiServiceSpanStartContext startContext() {
        return startContext;
    }

    @Override
    public Optional<Object> result() {
        return result;
    }
}
