package io.quarkiverse.langchain4j.prompt;

import java.util.List;

/**
 * Describes where a registry-backed prompt template is used by an AI service, so that registry extensions can validate
 * the usage eagerly (for example at application startup).
 *
 * @param reference the referenced template
 * @param boundVariables the names of the template variables that the AI service method binds (method parameters,
 *        {@code @V} names and the implicit variables provided by Quarkus LangChain4j)
 * @param location a human-readable description of the usage, e.g. {@code com.acme.Assistant#chat}
 */
public record PromptTemplateUsage(PromptTemplateReference reference, List<String> boundVariables, String location) {
}
