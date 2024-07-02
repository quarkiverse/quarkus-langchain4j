package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.List;

public record DeclarativeAiServiceCreateInfo(String serviceClassName,
        String languageModelSupplierClassName,
        List<String> toolsClassNames,
        String chatMemoryProviderSupplierClassName,
        String aiCacheProviderSupplierClassName,
        String retrieverClassName,
        String retrievalAugmentorSupplierClassName,
        String auditServiceClassSupplierName,
        String moderationModelSupplierClassName,
        String chatModelName,
        String moderationModelName,
        String embeddingModelName,
        boolean needsStreamingChatModel,
        boolean needsModerationModel,
        boolean enableCache) {
}
