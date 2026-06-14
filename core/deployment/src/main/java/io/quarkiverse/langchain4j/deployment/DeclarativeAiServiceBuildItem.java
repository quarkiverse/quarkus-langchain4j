package io.quarkiverse.langchain4j.deployment;

import java.util.List;
import java.util.Optional;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;

import io.quarkiverse.langchain4j.runtime.aiservice.ComponentResolutionMode;
import io.quarkus.builder.item.MultiBuildItem;

/**
 * Represents the metadata collected from the usages of {@link io.quarkiverse.langchain4j.RegisterAiService}
 */
public final class DeclarativeAiServiceBuildItem extends MultiBuildItem {

    private final ClassInfo serviceClassInfo;
    private final List<ClassInfo> toolClassInfos;

    // Component resolution fields — each has a DotName (nullable) and a resolution mode
    private final DotName chatMemoryProviderClassDotName;
    private final ComponentResolutionMode chatMemoryProviderResolutionMode;
    private final DotName chatMemoryFlushStrategyClassDotName;
    private final ComponentResolutionMode chatMemoryFlushStrategyResolutionMode;
    private final DotName retrievalAugmentorClassDotName;
    private final ComponentResolutionMode retrievalAugmentorResolutionMode;
    private final DotName moderationModelClassDotName;
    private final ComponentResolutionMode moderationModelResolutionMode;
    private final DotName imageModelClassDotName;
    private final ComponentResolutionMode imageModelResolutionMode;
    private final DotName toolProviderClassDotName;
    private final ComponentResolutionMode toolProviderResolutionMode;
    private final DotName toolSearchStrategyClassDotName;
    private final ComponentResolutionMode toolSearchStrategyResolutionMode;
    private final DotName toolHallucinationStrategyClassDotName;
    private final ComponentResolutionMode toolHallucinationStrategyResolutionMode;
    private final DotName systemMessageProviderClassDotName;
    private final ComponentResolutionMode systemMessageProviderResolutionMode;

    private final DotName chatMemorySeederClassDotName;
    private final DotName thinkingHandlerClassDotName;
    private final DotName cdiScope;
    private DotName defaultMemoryIdProviderClassDotName;
    private final String chatModelName;
    private final String moderationModelName;
    private final String imageModelName;
    private final Optional<String> beanName;
    private final DeclarativeAiServiceInputGuardrails inputGuardrails;
    private final DeclarativeAiServiceOutputGuardrails outputGuardrails;
    private final DotName toolArgumentsErrorHandlerDotName;
    private final DotName toolExecutionErrorHandlerDotName;
    private final Integer maxToolCallingRoundTrips;
    private final Integer maxToolCallsPerResponse;
    private final boolean allowContinuousForcedToolCalling;
    private final boolean makeDefaultBean;
    private final boolean shouldThrowExceptionOnEventError;

    public DeclarativeAiServiceBuildItem(
            ClassInfo serviceClassInfo,
            List<ClassInfo> toolClassInfos,
            DotName chatMemoryProviderClassDotName,
            ComponentResolutionMode chatMemoryProviderResolutionMode,
            DotName chatMemoryFlushStrategyClassDotName,
            ComponentResolutionMode chatMemoryFlushStrategyResolutionMode,
            DotName retrievalAugmentorClassDotName,
            ComponentResolutionMode retrievalAugmentorResolutionMode,
            DotName moderationModelClassDotName,
            ComponentResolutionMode moderationModelResolutionMode,
            DotName imageModelClassDotName,
            ComponentResolutionMode imageModelResolutionMode,
            DotName toolProviderClassDotName,
            ComponentResolutionMode toolProviderResolutionMode,
            DotName toolSearchStrategyClassDotName,
            ComponentResolutionMode toolSearchStrategyResolutionMode,
            DotName toolHallucinationStrategyClassDotName,
            ComponentResolutionMode toolHallucinationStrategyResolutionMode,
            DotName systemMessageProviderClassDotName,
            ComponentResolutionMode systemMessageProviderResolutionMode,
            DotName chatMemorySeederClassDotName,
            DotName thinkingHandlerClassDotName,
            DotName cdiScope,
            String chatModelName,
            String moderationModelName,
            String imageModelName,
            Optional<String> beanName,
            DeclarativeAiServiceInputGuardrails inputGuardrails,
            DeclarativeAiServiceOutputGuardrails outputGuardrails,
            DotName toolArgumentsErrorHandlerDotName,
            DotName toolExecutionErrorHandlerDotName,
            Integer maxToolCallingRoundTrips,
            Integer maxToolCallsPerResponse,
            boolean allowContinuousForcedToolCalling,
            boolean makeDefaultBean,
            boolean shouldThrowExceptionOnEventError) {
        this.serviceClassInfo = serviceClassInfo;
        this.toolClassInfos = toolClassInfos;
        this.chatMemoryProviderClassDotName = chatMemoryProviderClassDotName;
        this.chatMemoryProviderResolutionMode = chatMemoryProviderResolutionMode;
        this.chatMemoryFlushStrategyClassDotName = chatMemoryFlushStrategyClassDotName;
        this.chatMemoryFlushStrategyResolutionMode = chatMemoryFlushStrategyResolutionMode;
        this.retrievalAugmentorClassDotName = retrievalAugmentorClassDotName;
        this.retrievalAugmentorResolutionMode = retrievalAugmentorResolutionMode;
        this.moderationModelClassDotName = moderationModelClassDotName;
        this.moderationModelResolutionMode = moderationModelResolutionMode;
        this.imageModelClassDotName = imageModelClassDotName;
        this.imageModelResolutionMode = imageModelResolutionMode;
        this.toolProviderClassDotName = toolProviderClassDotName;
        this.toolProviderResolutionMode = toolProviderResolutionMode;
        this.toolSearchStrategyClassDotName = toolSearchStrategyClassDotName;
        this.toolSearchStrategyResolutionMode = toolSearchStrategyResolutionMode;
        this.toolHallucinationStrategyClassDotName = toolHallucinationStrategyClassDotName;
        this.toolHallucinationStrategyResolutionMode = toolHallucinationStrategyResolutionMode;
        this.systemMessageProviderClassDotName = systemMessageProviderClassDotName;
        this.systemMessageProviderResolutionMode = systemMessageProviderResolutionMode;
        this.chatMemorySeederClassDotName = chatMemorySeederClassDotName;
        this.thinkingHandlerClassDotName = thinkingHandlerClassDotName;
        this.cdiScope = cdiScope;
        this.chatModelName = chatModelName;
        this.moderationModelName = moderationModelName;
        this.imageModelName = imageModelName;
        this.beanName = beanName;
        this.inputGuardrails = inputGuardrails;
        this.outputGuardrails = outputGuardrails;
        this.toolArgumentsErrorHandlerDotName = toolArgumentsErrorHandlerDotName;
        this.toolExecutionErrorHandlerDotName = toolExecutionErrorHandlerDotName;
        this.maxToolCallingRoundTrips = maxToolCallingRoundTrips;
        this.maxToolCallsPerResponse = maxToolCallsPerResponse;
        this.allowContinuousForcedToolCalling = allowContinuousForcedToolCalling;
        this.makeDefaultBean = makeDefaultBean;
        this.shouldThrowExceptionOnEventError = shouldThrowExceptionOnEventError;
    }

    public ClassInfo getServiceClassInfo() {
        return serviceClassInfo;
    }

    public List<ClassInfo> getToolClassInfos() {
        return toolClassInfos;
    }

    public DotName getChatMemoryProviderClassDotName() {
        return chatMemoryProviderClassDotName;
    }

    public ComponentResolutionMode getChatMemoryProviderResolutionMode() {
        return chatMemoryProviderResolutionMode;
    }

    public DotName getChatMemoryFlushStrategyClassDotName() {
        return chatMemoryFlushStrategyClassDotName;
    }

    public ComponentResolutionMode getChatMemoryFlushStrategyResolutionMode() {
        return chatMemoryFlushStrategyResolutionMode;
    }

    public DotName getRetrievalAugmentorClassDotName() {
        return retrievalAugmentorClassDotName;
    }

    public ComponentResolutionMode getRetrievalAugmentorResolutionMode() {
        return retrievalAugmentorResolutionMode;
    }

    public DotName getModerationModelClassDotName() {
        return moderationModelClassDotName;
    }

    public ComponentResolutionMode getModerationModelResolutionMode() {
        return moderationModelResolutionMode;
    }

    public DotName getImageModelClassDotName() {
        return imageModelClassDotName;
    }

    public ComponentResolutionMode getImageModelResolutionMode() {
        return imageModelResolutionMode;
    }

    public DotName getToolProviderClassDotName() {
        return toolProviderClassDotName;
    }

    public ComponentResolutionMode getToolProviderResolutionMode() {
        return toolProviderResolutionMode;
    }

    public DotName getToolSearchStrategyClassDotName() {
        return toolSearchStrategyClassDotName;
    }

    public ComponentResolutionMode getToolSearchStrategyResolutionMode() {
        return toolSearchStrategyResolutionMode;
    }

    public DotName getToolHallucinationStrategyClassDotName() {
        return toolHallucinationStrategyClassDotName;
    }

    public ComponentResolutionMode getToolHallucinationStrategyResolutionMode() {
        return toolHallucinationStrategyResolutionMode;
    }

    public DotName getSystemMessageProviderClassDotName() {
        return systemMessageProviderClassDotName;
    }

    public ComponentResolutionMode getSystemMessageProviderResolutionMode() {
        return systemMessageProviderResolutionMode;
    }

    public DotName getChatMemorySeederClassDotName() {
        return chatMemorySeederClassDotName;
    }

    public DotName getThinkingHandlerClassDotName() {
        return thinkingHandlerClassDotName;
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

    public Optional<String> getBeanName() {
        return beanName;
    }

    public DeclarativeAiServiceInputGuardrails getInputGuardrails() {
        return inputGuardrails;
    }

    public DeclarativeAiServiceOutputGuardrails getOutputGuardrails() {
        return outputGuardrails;
    }

    public DotName getToolArgumentsErrorHandlerDotName() {
        return toolArgumentsErrorHandlerDotName;
    }

    public DotName getToolExecutionErrorHandlerDotName() {
        return toolExecutionErrorHandlerDotName;
    }

    public boolean isMakeDefaultBean() {
        return makeDefaultBean;
    }

    public boolean isShouldThrowExceptionOnEventError() {
        return shouldThrowExceptionOnEventError;
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

    public Integer getMaxToolCallingRoundTrips() {
        return maxToolCallingRoundTrips;
    }

    public Integer getMaxToolCallsPerResponse() {
        return maxToolCallsPerResponse;
    }

    public boolean isAllowContinuousForcedToolCalling() {
        return allowContinuousForcedToolCalling;
    }

    public DotName getDefaultMemoryIdProviderClassDotName() {
        return defaultMemoryIdProviderClassDotName;
    }

    public void setDefaultMemoryIdProviderClassDotName(DotName defaultMemoryIdProviderClassDotName) {
        this.defaultMemoryIdProviderClassDotName = defaultMemoryIdProviderClassDotName;
    }
}
