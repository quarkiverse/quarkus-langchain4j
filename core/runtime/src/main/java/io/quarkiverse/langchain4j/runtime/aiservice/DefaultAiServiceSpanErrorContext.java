package io.quarkiverse.langchain4j.runtime.aiservice;

final class DefaultAiServiceSpanErrorContext implements AiServiceSpanErrorContext {

    private final AiServiceSpanStartContext startContext;
    private final Throwable error;

    DefaultAiServiceSpanErrorContext(Builder builder) {
        this.startContext = builder.startContext;
        this.error = builder.error;
    }

    @Override
    public AiServiceSpanStartContext startContext() {
        return startContext;
    }

    @Override
    public Throwable error() {
        return error;
    }
}
