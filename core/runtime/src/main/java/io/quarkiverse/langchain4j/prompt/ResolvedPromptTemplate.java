package io.quarkiverse.langchain4j.prompt;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A prompt template resolved from a registry.
 *
 * @param text the template text, using the same syntax as inline {@code @SystemMessage} / {@code @UserMessage}
 *        templates (Qute, with {@code {{variable}}} placeholders also accepted)
 * @param version the version of the artifact that was resolved, may be {@code null} if unknown
 * @param variables the variables declared by the template, keyed by name (never {@code null})
 */
public record ResolvedPromptTemplate(String text, String version, Map<String, PromptVariable> variables) {

    public ResolvedPromptTemplate {
        if (text == null) {
            throw new IllegalArgumentException("The text of a prompt template must not be null");
        }
        variables = variables == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(variables));
    }

    public static ResolvedPromptTemplate of(String text) {
        return new ResolvedPromptTemplate(text, null, Map.of());
    }

    /**
     * @return the names of the variables that are declared as required (and have no default value) but are not
     *         part of {@code boundVariables}
     */
    public List<String> missingRequiredVariables(Collection<String> boundVariables) {
        return variables.values().stream()
                .filter(PromptVariable::required)
                .filter(v -> !v.hasDefaultValue())
                .map(PromptVariable::name)
                .filter(name -> !boundVariables.contains(name))
                .toList();
    }
}
