package io.quarkiverse.langchain4j.runtime.aiservice;

/**
 * The failure of an AI service invocation, as seen by an {@link AiServiceSpanContributor}.
 */
public sealed interface AiServiceSpanErrorContext permits DefaultAiServiceSpanErrorContext {

    /**
     * Creates a builder for an {@code AiServiceSpanErrorContext}.
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * The start of the invocation.
     */
    AiServiceSpanStartContext startContext();

    /**
     * The failure that ended the invocation.
     */
    Throwable error();

    /**
     * Creates a builder initialized with the values of this context.
     */
    default Builder toBuilder() {
        return new Builder(this);
    }

    /**
     * A builder for an {@code AiServiceSpanErrorContext}.
     */
    class Builder {
        AiServiceSpanStartContext startContext;
        Throwable error;

        private Builder() {
        }

        private Builder(AiServiceSpanErrorContext context) {
            this.startContext = context.startContext();
            this.error = context.error();
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
         * Sets the failure that ended the invocation.
         *
         * @param error the failure
         * @return this builder
         */
        public Builder error(Throwable error) {
            this.error = error;
            return this;
        }

        /**
         * Builds the context.
         *
         * @return a new {@code AiServiceSpanErrorContext}
         */
        public AiServiceSpanErrorContext build() {
            return new DefaultAiServiceSpanErrorContext(this);
        }
    }
}
