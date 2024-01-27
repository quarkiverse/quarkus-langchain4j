package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.List;

import io.quarkus.runtime.annotations.RecordableConstructor;

public class DeclarativeAiServiceCreateInfo {
    private final String serviceClassName;

    private final String languageModelSupplierClassName;
    private final List<String> toolsClassNames;
    private final String chatMemoryProviderSupplierClassName;
    private final String retrieverClassName;

    private final String auditServiceClassSupplierName;
    private final String moderationModelSupplierClassName;

    @RecordableConstructor
    public DeclarativeAiServiceCreateInfo(String serviceClassName, String languageModelSupplierClassName,
            List<String> toolsClassNames, String chatMemoryProviderSupplierClassName,
            String retrieverClassName,
            String auditServiceClassSupplierName,
            String moderationModelSupplierClassName) {
        this.serviceClassName = serviceClassName;
        this.languageModelSupplierClassName = languageModelSupplierClassName;
        this.toolsClassNames = toolsClassNames;
        this.chatMemoryProviderSupplierClassName = chatMemoryProviderSupplierClassName;
        this.retrieverClassName = retrieverClassName;
        this.auditServiceClassSupplierName = auditServiceClassSupplierName;
        this.moderationModelSupplierClassName = moderationModelSupplierClassName;
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

    public String getAuditServiceClassSupplierName() {
        return auditServiceClassSupplierName;
    }

    public String getModerationModelSupplierClassName() {
        return moderationModelSupplierClassName;
    }
}
