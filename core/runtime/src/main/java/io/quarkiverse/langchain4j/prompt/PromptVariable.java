package io.quarkiverse.langchain4j.prompt;

/**
 * A variable declared by a prompt template stored in a registry.
 *
 * @param name the name of the variable, as used in the template
 * @param type the declared type (e.g. {@code string}, {@code integer}), may be {@code null}
 * @param required whether the variable must be provided when rendering the template
 * @param defaultValue the value to use when the variable is not provided, may be {@code null}
 * @param description a human-readable description, may be {@code null}
 */
public record PromptVariable(String name, String type, boolean required, Object defaultValue, String description) {

    public boolean hasDefaultValue() {
        return defaultValue != null;
    }
}
