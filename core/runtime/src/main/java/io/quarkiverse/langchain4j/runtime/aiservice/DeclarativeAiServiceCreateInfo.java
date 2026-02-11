package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.Map;

import jakarta.enterprise.util.AnnotationLiteral;

import io.quarkiverse.langchain4j.guardrails.InputGuardrailsLiteral;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailsLiteral;

public record DeclarativeAiServiceCreateInfo(
        String serviceClassName,
        String languageModelSupplierClassName,
        String streamingChatLanguageModelSupplierClassName,
        Map<String, AnnotationLiteral<?>> toolsClassInfo,
        String toolProviderSupplier,
        String chatMemoryProviderSupplierClassName,
        String retrievalAugmentorSupplierClassName,
        String moderationModelSupplierClassName,
        String imageModelSupplierClassName,
        String chatMemorySeederClassName,
        String systemMessageProviderClassName,
        String chatModelName,
        String moderationModelName,
        String imageModelName,
        boolean needsStreamingChatModel,
        boolean needsModerationModel,
        boolean needsImageModel,
        String toolHallucinationStrategyClassName,
        String toolArgumentsErrorHandlerClassName,
        String toolExecutionErrorHandlerClassName,
        InputGuardrailsLiteral inputGuardrails,
        OutputGuardrailsLiteral outputGuardrails,
        Integer maxSequentialToolInvocations,
        boolean allowContinuousForcedToolCalling) {
}
