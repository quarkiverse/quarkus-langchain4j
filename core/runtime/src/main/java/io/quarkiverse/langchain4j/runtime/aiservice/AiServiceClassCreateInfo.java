package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.Map;

import io.quarkiverse.langchain4j.guardrails.InputGuardrailsLiteral;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailsLiteral;

/**
 * @param methodMap the key is a methodId generated at build time
 * @param aiServiceName the canonical AiService name used as the per-service config key under
 *        {@code quarkus.langchain4j.<ai-service-name>.*}. Resolved at build time as the {@code @Named} bean name
 *        when present on the AiService interface, otherwise the simple class name. Never {@code null} for entries
 *        produced by the deployment processor; may be {@code null} for legacy {@code AiServiceClassCreateInfo}
 *        instances built outside the standard processor path.
 */
public record AiServiceClassCreateInfo(Map<String, AiServiceMethodCreateInfo> methodMap, String implClassName,
        InputGuardrailsLiteral inputGuardrails, OutputGuardrailsLiteral outputGuardrails, String aiServiceName) {
}
