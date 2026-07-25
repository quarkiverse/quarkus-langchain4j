package io.quarkiverse.langchain4j.deployment.devui;

import java.util.List;

/**
 * Build-time metadata for a single guardrail class, aggregated across everything that uses it.
 * Powers the Dev UI "Guardrails" page, which is organized by guardrail class.
 *
 * @param className the fully qualified guardrail class name
 * @param kind {@code "Input"}, {@code "Output"}, {@code "Tool input"} or {@code "Tool output"}
 * @param usedBy the AI services and tool methods applying this guardrail
 */
public record GuardrailInfo(String className, String kind, List<GuardrailUsage> usedBy) {
}
