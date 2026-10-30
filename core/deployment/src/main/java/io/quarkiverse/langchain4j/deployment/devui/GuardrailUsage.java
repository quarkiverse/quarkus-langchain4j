package io.quarkiverse.langchain4j.deployment.devui;

import java.util.List;

/**
 * A single application of a guardrail, either by an AI service or by a tool method.
 *
 * @param owner the fully qualified name of the AI service interface or tool class applying the guardrail
 * @param method the method the guardrail is declared on, or {@code null} when it is declared at the AI service class
 *        level (and thus applies to every method of the service); tool guardrails are always method-level
 * @param position the 1-based position of the guardrail in that input/output chain
 * @param maxRetries the max retries explicitly set on the output guardrail annotation, or {@code null} for input
 *        guardrails, tool guardrails, and output guardrails that rely on the default/configured value
 * @param excludedMethods for a class-level guardrail, the methods that declare their own guardrails of the same kind
 *        and therefore override (and do not run) it; empty otherwise. A method-level annotation overrides the
 *        class-level one of the same kind rather than adding to it.
 */
public record GuardrailUsage(String owner, String method, int position, Integer maxRetries, List<String> excludedMethods) {
}
