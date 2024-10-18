package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.List;

public record DeclarativeAiServiceCreateInfo(
        String serviceClassName,
        String languageModelSupplierClassName,
        String streamingChatLanguageModelSupplierClassName,
        List<String> toolsClassNames,
        String toolProviderSupplier,
        String chatMemoryProviderSupplierClassName,
        String retrieverClassName,
        String retrievalAugmentorSupplierClassName,
        String auditServiceClassSupplierName,
        String moderationModelSupplierClassName,
        String imageModelSupplierClassName,
        String chatMemorySeederClassName,
        String chatModelName,
        String moderationModelName,
        String imageModelName,
        boolean needsStreamingChatModel,
        boolean needsModerationModel,
        boolean needsImageModel) {
}
