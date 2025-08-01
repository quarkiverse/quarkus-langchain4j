package io.quarkiverse.langchain4j.deployment;

import java.util.List;
import java.util.Optional;

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
    private final List<ClassInfo> toolClassInfos;
    private final DotName toolProviderClassDotName;
    private final DotName toolHallucinationStrategyClassDotName;

    private final DotName chatMemoryProviderSupplierClassDotName;
    private final DotName retrievalAugmentorSupplierClassDotName;
    private final boolean customRetrievalAugmentorSupplierClassIsABean;
    private final DotName moderationModelSupplierDotName;
    private final DotName imageModelSupplierDotName;
    private final DotName chatMemorySeederClassDotName;
    private final DotName cdiScope;
    private final String chatModelName;
    private final String moderationModelName;
    private final String imageModelName;
    private final Optional<String> beanName;
    private final DeclarativeAiServiceInputGuardrails inputGuardrails;
    private final DeclarativeAiServiceOutputGuardrails outputGuardrails;

    public DeclarativeAiServiceBuildItem(
            ClassInfo serviceClassInfo,
            DotName chatLanguageModelSupplierClassDotName,
            DotName streamingChatLanguageModelSupplierClassDotName,
            List<ClassInfo> toolClassInfos,
            DotName chatMemoryProviderSupplierClassDotName,
            DotName retrievalAugmentorSupplierClassDotName,
            boolean customRetrievalAugmentorSupplierClassIsABean,
            DotName moderationModelSupplierDotName,
            DotName imageModelSupplierDotName,
            DotName chatMemorySeederClassDotName,
            DotName cdiScope,
            String chatModelName,
            String moderationModelName,
            String imageModelName,
            DotName toolProviderClassDotName,
            Optional<String> beanName,
            DotName toolHallucinationStrategyClassDotName,
            DeclarativeAiServiceInputGuardrails inputGuardrails,
            DeclarativeAiServiceOutputGuardrails outputGuardrails) {
        this.serviceClassInfo = serviceClassInfo;
        this.chatLanguageModelSupplierClassDotName = chatLanguageModelSupplierClassDotName;
        this.streamingChatLanguageModelSupplierClassDotName = streamingChatLanguageModelSupplierClassDotName;
        this.toolClassInfos = toolClassInfos;
        this.chatMemoryProviderSupplierClassDotName = chatMemoryProviderSupplierClassDotName;
        this.retrievalAugmentorSupplierClassDotName = retrievalAugmentorSupplierClassDotName;
        this.customRetrievalAugmentorSupplierClassIsABean = customRetrievalAugmentorSupplierClassIsABean;
        this.moderationModelSupplierDotName = moderationModelSupplierDotName;
        this.imageModelSupplierDotName = imageModelSupplierDotName;
        this.chatMemorySeederClassDotName = chatMemorySeederClassDotName;
        this.cdiScope = cdiScope;
        this.chatModelName = chatModelName;
        this.moderationModelName = moderationModelName;
        this.imageModelName = imageModelName;
        this.toolProviderClassDotName = toolProviderClassDotName;
        this.beanName = beanName;
        this.toolHallucinationStrategyClassDotName = toolHallucinationStrategyClassDotName;
        this.inputGuardrails = inputGuardrails;
        this.outputGuardrails = outputGuardrails;
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

    public List<ClassInfo> getToolClassInfos() {
        return toolClassInfos;
    }

    public DotName getChatMemoryProviderSupplierClassDotName() {
        return chatMemoryProviderSupplierClassDotName;
    }

    public DotName getRetrievalAugmentorSupplierClassDotName() {
        return retrievalAugmentorSupplierClassDotName;
    }

    public boolean isCustomRetrievalAugmentorSupplierClassIsABean() {
        return customRetrievalAugmentorSupplierClassIsABean;
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

    public DotName getToolProviderClassDotName() {
        return toolProviderClassDotName;
    }

    public Optional<String> getBeanName() {
        return beanName;
    }

    public DotName getToolHallucinationStrategyClassDotName() {
        return toolHallucinationStrategyClassDotName;
    }

    public DeclarativeAiServiceInputGuardrails getInputGuardrails() {
        return inputGuardrails;
    }

    public DeclarativeAiServiceOutputGuardrails getOutputGuardrails() {
        return outputGuardrails;
    }

    public record DeclarativeAiServiceInputGuardrails(List<ClassInfo> inputGuardrailClassInfos) {
        public List<String> asClassNames() {
            return this.inputGuardrailClassInfos.stream()
                    .map(classInfo -> classInfo.name().toString())
                    .toList();
        }
    }

    public record DeclarativeAiServiceOutputGuardrails(List<ClassInfo> outputGuardrailClassInfos, int maxRetries,
            int actualMaxRetries) {
        public DeclarativeAiServiceOutputGuardrails(List<ClassInfo> outputGuardrailClassInfos, int maxRetries) {
            this(outputGuardrailClassInfos, maxRetries, maxRetries);
        }

        public List<String> asClassNames() {
            return this.outputGuardrailClassInfos.stream()
                    .map(classInfo -> classInfo.name().toString())
                    .toList();
        }
    }
}
