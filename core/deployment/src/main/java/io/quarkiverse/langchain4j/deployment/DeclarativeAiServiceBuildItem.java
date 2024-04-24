package io.quarkiverse.langchain4j.deployment;

import java.util.List;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * Represents the metadata collected from the usages of {@link io.quarkiverse.langchain4j.RegisterAiService}
 */
public final class DeclarativeAiServiceBuildItem extends MultiBuildItem {

    private final ClassInfo serviceClassInfo;
    private final DotName languageModelSupplierClassDotName;
    private final List<DotName> toolDotNames;

    private final DotName chatMemoryProviderSupplierClassDotName;
    private final DotName retrieverClassDotName;
    private final DotName retrievalAugmentorSupplierClassDotName;
    private final boolean customRetrievalAugmentorSupplierClassIsABean;
    private final DotName auditServiceClassSupplierDotName;
    private final DotName moderationModelSupplierDotName;
    private final DotName cdiScope;
    private final String chatModelName;
    private final String moderationModelName;

    public DeclarativeAiServiceBuildItem(ClassInfo serviceClassInfo, DotName languageModelSupplierClassDotName,
            List<DotName> toolDotNames,
            DotName chatMemoryProviderSupplierClassDotName,
            DotName retrieverClassDotName,
            DotName retrievalAugmentorSupplierClassDotName,
            boolean customRetrievalAugmentorSupplierClassIsABean,
            DotName auditServiceClassSupplierDotName,
            DotName moderationModelSupplierDotName,
            DotName cdiScope,
            String chatModelName,
            String moderationModelName) {
        this.serviceClassInfo = serviceClassInfo;
        this.languageModelSupplierClassDotName = languageModelSupplierClassDotName;
        this.toolDotNames = toolDotNames;
        this.chatMemoryProviderSupplierClassDotName = chatMemoryProviderSupplierClassDotName;
        this.retrieverClassDotName = retrieverClassDotName;
        this.retrievalAugmentorSupplierClassDotName = retrievalAugmentorSupplierClassDotName;
        this.customRetrievalAugmentorSupplierClassIsABean = customRetrievalAugmentorSupplierClassIsABean;
        this.auditServiceClassSupplierDotName = auditServiceClassSupplierDotName;
        this.moderationModelSupplierDotName = moderationModelSupplierDotName;
        this.cdiScope = cdiScope;
        this.chatModelName = chatModelName;
        this.moderationModelName = moderationModelName;
    }

    public ClassInfo getServiceClassInfo() {
        return serviceClassInfo;
    }

    public DotName getLanguageModelSupplierClassDotName() {
        return languageModelSupplierClassDotName;
    }

    public List<DotName> getToolDotNames() {
        return toolDotNames;
    }

    public DotName getChatMemoryProviderSupplierClassDotName() {
        return chatMemoryProviderSupplierClassDotName;
    }

    public DotName getRetrieverClassDotName() {
        return retrieverClassDotName;
    }

    public DotName getRetrievalAugmentorSupplierClassDotName() {
        return retrievalAugmentorSupplierClassDotName;
    }

    public boolean isCustomRetrievalAugmentorSupplierClassIsABean() {
        return customRetrievalAugmentorSupplierClassIsABean;
    }

    public DotName getAuditServiceClassSupplierDotName() {
        return auditServiceClassSupplierDotName;
    }

    public DotName getModerationModelSupplierDotName() {
        return moderationModelSupplierDotName;
    }

    public DotName getCdiScope() {
        return cdiScope;
    }

    public String getChatModelName() {
        return chatModelName;
    }

    public String getModerationModelName() {
        return moderationModelName;
    }
}
