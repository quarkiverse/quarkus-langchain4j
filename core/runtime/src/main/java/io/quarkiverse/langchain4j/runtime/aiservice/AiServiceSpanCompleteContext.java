package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.Optional;

/**
 * The completion of an AI service invocation, as seen by an {@link AiServiceSpanContributor}.
 */
public sealed interface AiServiceSpanCompleteContext permits DefaultAiServiceSpanCompleteContext {

    /**
     * Creates a builder for an {@code AiServiceSpanCompleteContext}.
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * The start of the invocation.
     */
    AiServiceSpanStartContext startContext();

    /**
     * The result of the invocation.
     * <p>
     * For a method that returns a value, this is the returned value, empty when it is {@code null}.
     * For a streaming method, this is the streamed text, collected only when
     * {@code quarkus.langchain4j.tracing.include-ai-service-result} is enabled, and empty otherwise or when the stream is
     * canceled.
     */
    Optional<Object> result();

    /**
     * Creates a builder initialized with the values of this context.
     */
    default Builder toBuilder() {
        return new Builder(this);
    }

    /**
     * A builder for an {@code AiServiceSpanCompleteContext}.
     */
    class Builder {
        AiServiceSpanStartContext startContext;
        Object result;

        private Builder() {
        }

        private Builder(AiServiceSpanCompleteContext context) {
            this.startContext = context.startContext();
            this.result = context.result().orElse(null);
        }

        /**
         * Sets the start of the invocation.
         *
         * @param startContext the start context
         * @return this builder
         */
        public Builder startContext(AiServiceSpanStartContext startContext) {
            this.startContext = startContext;
            return this;
        }

        /**
         * Sets the result of the invocation.
         *
         * @param result the result, or {@code null} when there is none
         * @return this builder
         */
        public Builder result(Object result) {
            this.result = result;
            return this;
        }

        /**
         * Builds the context.
         *
         * @return a new {@code AiServiceSpanCompleteContext}
         */
        public AiServiceSpanCompleteContext build() {
            return new DefaultAiServiceSpanCompleteContext(this);
        }
    }
}
