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
    private final DotName retrieverSupplierClassDotName;

    public DeclarativeAiServiceBuildItem(ClassInfo serviceClassInfo, DotName languageModelSupplierClassDotName,
            List<DotName> toolDotNames,
            DotName chatMemoryProviderSupplierClassDotName,
            DotName retrieverSupplierClassDotName) {
        this.serviceClassInfo = serviceClassInfo;
        this.languageModelSupplierClassDotName = languageModelSupplierClassDotName;
        this.toolDotNames = toolDotNames;
        this.chatMemoryProviderSupplierClassDotName = chatMemoryProviderSupplierClassDotName;
        this.retrieverSupplierClassDotName = retrieverSupplierClassDotName;
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

    public DotName getRetrieverSupplierClassDotName() {
        return retrieverSupplierClassDotName;
    }
}
