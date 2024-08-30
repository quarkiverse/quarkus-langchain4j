package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.List;

public record DeclarativeAiServiceCreateInfo(
        String serviceClassName,
        String languageModelSupplierClassName,
        String streamingChatLanguageModelSupplierClassName,
        List<String> toolsClassNames,
        String chatMemoryProviderSupplierClassName,
        String retrieverClassName,
        String retrievalAugmentorSupplierClassName,
        String auditServiceClassSupplierName,
        String moderationModelSupplierClassName,
        String chatMemorySeederClassName,
        String chatModelName,
        String moderationModelName,
        boolean needsStreamingChatModel,
        boolean needsModerationModel) {
}
