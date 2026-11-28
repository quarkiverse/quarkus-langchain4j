package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.List;

/**
 * The start of an AI service invocation, as seen by an {@link AiServiceSpanContributor}.
 */
public sealed interface AiServiceSpanStartContext permits DefaultAiServiceSpanStartContext {

    /**
     * Creates a builder for an {@code AiServiceSpanStartContext}.
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * The fully qualified name of the AI service interface.
     */
    String interfaceName();

    /**
     * The name of the invoked AI service method.
     */
    String methodName();

    /**
     * All the arguments the method was invoked with, in declaration order, including the ones that are not recorded on
     * the span, such as the memory id. Elements may be {@code null}.
     *
     * @return an unmodifiable list of the method arguments
     */
    List<Object> methodArguments();

    /**
     * Creates a builder initialized with the values of this context.
     */
    default Builder toBuilder() {
        return new Builder(this);
    }

    /**
     * A builder for an {@code AiServiceSpanStartContext}.
     */
    class Builder {
        String interfaceName;
        String methodName;
        List<Object> methodArguments = List.of();

        private Builder() {
        }

        private Builder(AiServiceSpanStartContext context) {
            this.interfaceName = context.interfaceName();
            this.methodName = context.methodName();
            this.methodArguments = context.methodArguments();
        }

        /**
         * Sets the fully qualified name of the AI service interface.
         *
         * @param interfaceName the interface name
         * @return this builder
         */
        public Builder interfaceName(String interfaceName) {
            this.interfaceName = interfaceName;
            return this;
        }

        /**
         * Sets the name of the invoked method.
         *
         * @param methodName the method name
         * @return this builder
         */
        public Builder methodName(String methodName) {
            this.methodName = methodName;
            return this;
        }

        /**
         * Sets the arguments the method was invoked with.
         *
         * @param methodArguments the method arguments, in declaration order; elements may be {@code null}
         * @return this builder
         */
        public Builder methodArguments(List<Object> methodArguments) {
            this.methodArguments = methodArguments;
            return this;
        }

        /**
         * Builds the context.
         *
         * @return a new {@code AiServiceSpanStartContext}
         */
        public AiServiceSpanStartContext build() {
            return new DefaultAiServiceSpanStartContext(this);
        }
    }
}
