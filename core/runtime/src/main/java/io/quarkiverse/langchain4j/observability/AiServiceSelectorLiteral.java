package io.quarkiverse.langchain4j.observability;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import jakarta.enterprise.util.AnnotationLiteral;

/**
 * A concrete implementation of {@link AiServiceSelector} and {@link AnnotationLiteral}, used to provide
 * a runtime version of the {@link AiServiceSelector} annotation. This class is primarily used as a literal
 * for contextual injection where an {@link AiServiceSelector} annotation is required, allowing the
 * specification of the AI service class at runtime.
 */
public class AiServiceSelectorLiteral extends AnnotationLiteral<AiServiceSelector> implements AiServiceSelector {
    private final Class<?> aiServiceClass;

    protected AiServiceSelectorLiteral(Builder builder) {
        this.aiServiceClass = ensureNotNull(builder.aiServiceClass, "aiServiceClass");
    }

    @Override
    public Class<?> value() {
        return aiServiceClass();
    }

    /**
     * Retrieves the AI service class specified for this instance.
     *
     * @return the class object representing the AI service interface associated with this instance
     */
    public Class<?> aiServiceClass() {
        return this.aiServiceClass;
    }

    /**
     * Creates a new {@link Builder} initialized with the current state of this instance.
     *
     * @return a {@link Builder} instance pre-configured with the properties of this {@code AiServiceSelector}
     */
    public Builder toBuilder() {
        return new Builder(this);
    }

    /**
     * Creates and returns a new instance of the {@link Builder} class, which can be used
     * to construct an {@link AiServiceSelector} instance with specified properties.
     *
     * @return a new {@link Builder} instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for constructing instances of {@link AiServiceSelector}.
     * Provides a fluent API to configure its properties.
     */
    public static class Builder {
        private Class<?> aiServiceClass;

        private Builder() {
        }

        private Builder(AiServiceSelector aiServiceSelector) {
            this.aiServiceClass = aiServiceSelector.value();
        }

        /**
         * Sets the AI service class to be used in the construction of the {@link AiServiceSelector}.
         *
         * @param aiServiceClass the class object representing the AI service interface
         * @return the {@link Builder} instance for method chaining
         */
        public Builder aiServiceClass(Class<?> aiServiceClass) {
            this.aiServiceClass = aiServiceClass;
            return this;
        }

        /**
         * Constructs and returns a new instance of {@link AiServiceSelector}.
         * The returned instance represents a runtime version of the {@link AiServiceSelector} annotation,
         * based on the properties configured in the {@link Builder}.
         *
         * @return a new {@link AiServiceSelector} instance constructed with the current state of the {@link Builder}
         */
        public AiServiceSelector build() {
            return new AiServiceSelectorLiteral(this);
        }
    }
}
