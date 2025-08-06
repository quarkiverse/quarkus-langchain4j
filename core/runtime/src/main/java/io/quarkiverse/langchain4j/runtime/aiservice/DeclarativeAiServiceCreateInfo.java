package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.Map;

import jakarta.enterprise.util.AnnotationLiteral;

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
        String chatModelName,
        String moderationModelName,
        String imageModelName,
        boolean needsStreamingChatModel,
        boolean needsModerationModel,
        boolean needsImageModel,
        String toolHallucinationStrategyClassName,
        Integer maxSequentialToolInvocations) {
}
