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
    private final DotName chatLanguageModelSupplierClassDotName;
    private final DotName streamingChatLanguageModelSupplierClassDotName;
    private final List<DotName> toolDotNames;

    private final DotName chatMemoryProviderSupplierClassDotName;
    private final DotName retrieverClassDotName;
    private final DotName retrievalAugmentorSupplierClassDotName;
    private final boolean customRetrievalAugmentorSupplierClassIsABean;
    private final DotName auditServiceClassSupplierDotName;
    private final DotName moderationModelSupplierDotName;
    private final DotName imageModelSupplierDotName;
    private final DotName chatMemorySeederClassDotName;
    private final DotName cdiScope;
    private final String chatModelName;
    private final String moderationModelName;
    private final String imageModelName;

    public DeclarativeAiServiceBuildItem(
            ClassInfo serviceClassInfo,
            DotName chatLanguageModelSupplierClassDotName,
            DotName streamingChatLanguageModelSupplierClassDotName,
            List<DotName> toolDotNames,
            DotName chatMemoryProviderSupplierClassDotName,
            DotName retrieverClassDotName,
            DotName retrievalAugmentorSupplierClassDotName,
            boolean customRetrievalAugmentorSupplierClassIsABean,
            DotName auditServiceClassSupplierDotName,
            DotName moderationModelSupplierDotName,
            DotName imageModelSupplierDotName,
            DotName chatMemorySeederClassDotName,
            DotName cdiScope,
            String chatModelName,
            String moderationModelName,
            String imageModelName) {
        this.serviceClassInfo = serviceClassInfo;
        this.chatLanguageModelSupplierClassDotName = chatLanguageModelSupplierClassDotName;
        this.streamingChatLanguageModelSupplierClassDotName = streamingChatLanguageModelSupplierClassDotName;
        this.toolDotNames = toolDotNames;
        this.chatMemoryProviderSupplierClassDotName = chatMemoryProviderSupplierClassDotName;
        this.retrieverClassDotName = retrieverClassDotName;
        this.retrievalAugmentorSupplierClassDotName = retrievalAugmentorSupplierClassDotName;
        this.customRetrievalAugmentorSupplierClassIsABean = customRetrievalAugmentorSupplierClassIsABean;
        this.auditServiceClassSupplierDotName = auditServiceClassSupplierDotName;
        this.moderationModelSupplierDotName = moderationModelSupplierDotName;
        this.imageModelSupplierDotName = imageModelSupplierDotName;
        this.chatMemorySeederClassDotName = chatMemorySeederClassDotName;
        this.cdiScope = cdiScope;
        this.chatModelName = chatModelName;
        this.moderationModelName = moderationModelName;
        this.imageModelName = imageModelName;
    }

    public ClassInfo getServiceClassInfo() {
        return serviceClassInfo;
    }

    public DotName getChatLanguageModelSupplierClassDotName() {
        return chatLanguageModelSupplierClassDotName;
    }

    public DotName getStreamingChatLanguageModelSupplierClassDotName() {
        return streamingChatLanguageModelSupplierClassDotName;
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

    public DotName getImageModelSupplierDotName() {
        return imageModelSupplierDotName;
    }

    public DotName getChatMemorySeederClassDotName() {
        return chatMemorySeederClassDotName;
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

    public String getImageModelName() {
        return imageModelName;
    }
}
