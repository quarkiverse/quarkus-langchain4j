package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.List;

import io.quarkus.runtime.annotations.RecordableConstructor;

public class DeclarativeAiServiceCreateInfo {
    private final String serviceClassName;

    private final String languageModelSupplierClassName;
    private final List<String> toolsClassNames;
    private final String chatMemoryProviderSupplierClassName;
    private final String retrieverClassName;
    private final String retrievalAugmentorSupplierClassName;
    private final String auditServiceClassSupplierName;
    private final String moderationModelSupplierClassName;
    private final String chatModelName;
    private final boolean needsStreamingChatModel;

    @RecordableConstructor
    public DeclarativeAiServiceCreateInfo(String serviceClassName, String languageModelSupplierClassName,
            List<String> toolsClassNames, String chatMemoryProviderSupplierClassName,
            String retrieverClassName, String retrievalAugmentorSupplierClassName,
            String auditServiceClassSupplierName,
            String moderationModelSupplierClassName, String chatModelName,
            boolean needsStreamingChatModel) {
        this.needsStreamingChatModel = needsStreamingChatModel;
        this.serviceClassName = serviceClassName;
        this.languageModelSupplierClassName = languageModelSupplierClassName;
        this.toolsClassNames = toolsClassNames;
        this.chatMemoryProviderSupplierClassName = chatMemoryProviderSupplierClassName;
        this.retrieverClassName = retrieverClassName;
        this.retrievalAugmentorSupplierClassName = retrievalAugmentorSupplierClassName;
        this.auditServiceClassSupplierName = auditServiceClassSupplierName;
        this.moderationModelSupplierClassName = moderationModelSupplierClassName;
        this.chatModelName = chatModelName;
    }

    public String getServiceClassName() {
        return serviceClassName;
    }

    public String getLanguageModelSupplierClassName() {
        return languageModelSupplierClassName;
    }

    public List<String> getToolsClassNames() {
        return toolsClassNames;
    }

    public String getChatMemoryProviderSupplierClassName() {
        return chatMemoryProviderSupplierClassName;
    }

    public String getRetrieverClassName() {
        return retrieverClassName;
    }

    public String getRetrievalAugmentorSupplierClassName() {
        return retrievalAugmentorSupplierClassName;
    }

    public String getAuditServiceClassSupplierName() {
        return auditServiceClassSupplierName;
    }

    public String getModerationModelSupplierClassName() {
        return moderationModelSupplierClassName;
    }

    public String getChatModelName() {
        return chatModelName;
    }

    public boolean getNeedsStreamingChatModel() {
        return needsStreamingChatModel;
    }
}
