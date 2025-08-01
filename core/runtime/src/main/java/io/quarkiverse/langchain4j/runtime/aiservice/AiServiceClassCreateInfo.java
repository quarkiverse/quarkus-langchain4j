package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.Map;

import io.quarkiverse.langchain4j.guardrails.InputGuardrailsLiteral;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailsLiteral;

/**
 * @param methodMap the key is a methodId generated at build time
 */
public record AiServiceClassCreateInfo(Map<String, AiServiceMethodCreateInfo> methodMap, String implClassName,
        InputGuardrailsLiteral inputGuardrails, OutputGuardrailsLiteral outputGuardrails) {
}
