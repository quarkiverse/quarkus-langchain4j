package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.List;

import io.quarkus.runtime.annotations.RecordableConstructor;

public class DeclarativeAiServiceCreateInfo {
    private final String serviceClassName;

    private final String languageModelSupplierClassName;
    private final List<String> toolsClassNames;
    private final String chatMemoryProviderSupplierClassName;
    private final String retrieverSupplierClassName;

    private final String auditServiceClassSupplierName;

    @RecordableConstructor
    public DeclarativeAiServiceCreateInfo(String serviceClassName, String languageModelSupplierClassName,
            List<String> toolsClassNames, String chatMemoryProviderSupplierClassName,
            String retrieverSupplierClassName,
            String auditServiceClassSupplierName) {
        this.serviceClassName = serviceClassName;
        this.languageModelSupplierClassName = languageModelSupplierClassName;
        this.toolsClassNames = toolsClassNames;
        this.chatMemoryProviderSupplierClassName = chatMemoryProviderSupplierClassName;
        this.retrieverSupplierClassName = retrieverSupplierClassName;
        this.auditServiceClassSupplierName = auditServiceClassSupplierName;
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

    public String getRetrieverSupplierClassName() {
        return retrieverSupplierClassName;
    }

    public String getAuditServiceClassSupplierName() {
        return auditServiceClassSupplierName;
    }
}
