package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.Map;

import jakarta.enterprise.util.AnnotationLiteral;

import io.quarkiverse.langchain4j.guardrails.InputGuardrailsLiteral;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailsLiteral;
import io.quarkiverse.langchain4j.runtime.rag.RagPipelineCreateInfo;

public record DeclarativeAiServiceCreateInfo(
        String serviceClassName,
        Map<String, AnnotationLiteral<?>> toolsClassInfo,
        ComponentEntry chatMemoryProvider,
        ComponentEntry chatMemoryFlushStrategy,
        RagPipelineCreateInfo ragPipelineCreateInfo,
        ComponentEntry moderationModel,
        ComponentEntry imageModel,
        ComponentEntry toolProvider,
        ComponentEntry toolSearchStrategy,
        ComponentEntry toolHallucinationStrategy,
        ComponentEntry systemMessageProvider,
        String chatMemorySeederClassName,
        String thinkingHandlerClassName,
        String chatModelName,
        String moderationModelName,
        String imageModelName,
        boolean needsChatModel,
        boolean needsStreamingChatModel,
        boolean needsModerationModel,
        boolean needsImageModel,
        String toolArgumentsErrorHandlerClassName,
        String toolExecutionErrorHandlerClassName,
        InputGuardrailsLiteral inputGuardrails,
        OutputGuardrailsLiteral outputGuardrails,
        Integer maxToolCallingRoundTrips,
        Integer maxToolCallsPerResponse,
        boolean allowContinuousForcedToolCalling,
        boolean shouldThrowExceptionOnEventError,
        String defaultMemoryIdProviderClassName) {

    /**
     * Pairs a class name with its resolution mode. Used for all component attributes
     * that follow the tri-state resolution model (void = SKIP, interface = AUTO_DISCOVER,
     * concrete class = EXPLICIT).
     */
    public record ComponentEntry(String className, ComponentResolutionMode mode) {
        public static final ComponentEntry SKIP = new ComponentEntry(null, ComponentResolutionMode.SKIP);
    }
}
