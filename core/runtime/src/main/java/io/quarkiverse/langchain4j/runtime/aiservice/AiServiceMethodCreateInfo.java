package io.quarkiverse.langchain4j.runtime.aiservice;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import jakarta.enterprise.util.AnnotationLiteral;

import org.eclipse.microprofile.config.ConfigProvider;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.service.tool.ToolExecutor;
import io.quarkiverse.langchain4j.guardrails.InputGuardrailsLiteral;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailsLiteral;
import io.quarkiverse.langchain4j.guardrails.OutputTokenAccumulator;
import io.quarkiverse.langchain4j.response.AiResponseAugmenter;
import io.quarkiverse.langchain4j.runtime.ResponseSchemaUtil;
import io.quarkiverse.langchain4j.runtime.config.GuardrailsConfig;
import io.quarkiverse.langchain4j.runtime.types.TypeSignatureParser;
import io.quarkus.arc.impl.LazyValue;
import io.quarkus.runtime.annotations.RecordableConstructor;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public final class AiServiceMethodCreateInfo {
    private final String interfaceName;
    private final String methodName;
    private final Optional<TemplateInfo> systemMessageInfo;
    private final UserMessageInfo userMessageInfo;
    private final Optional<Integer> memoryIdParamPosition;
    private final boolean requiresModeration;
    private final String returnTypeSignature; // transient so bytecode recording ignores it
    private final Optional<Integer> overrideChatModelParamPosition;
    private final transient LazyValue<Type> returnTypeVal; // transient so bytecode recording ignores it
    private final Optional<MetricsTimedInfo> metricsTimedInfo;
    private final Optional<MetricsCountedInfo> metricsCountedInfo;
    private final Optional<SpanInfo> spanInfo;
    // support @Toolbox
    private final Map<String, AnnotationLiteral<?>> toolClassInfo;
    private final List<String> mcpClientNames;
    private final ResponseSchemaInfo responseSchemaInfo;

    // support for guardrails
    private final InputGuardrailsLiteral inputGuardrails;
    private final OutputGuardrailsLiteral outputGuardrails;

    // support for response augmenter, potentially null
    private final String responseAugmenterClassName;

    // these are populated when the AiService method is first called which can happen on any thread
    private transient final List<ToolSpecification> toolSpecifications = new CopyOnWriteArrayList<>();
    private transient final Map<String, ToolExecutor> toolExecutors = new ConcurrentHashMap<>();

    // Don't cache the instances, because of scope issues (some will need to be re-queried)
    private transient Class<? extends AiResponseAugmenter<?>> augmenter;

    private final String outputTokenAccumulatorClassName;
    private OutputTokenAccumulator accumulator;

    private final LazyValue<Integer> quarkusGuardrailsMaxRetry;
    private final boolean switchToWorkerThreadForToolExecution;

    @RecordableConstructor
    public AiServiceMethodCreateInfo(String interfaceName, String methodName,
            Optional<TemplateInfo> systemMessageInfo,
            UserMessageInfo userMessageInfo,
            Optional<Integer> memoryIdParamPosition,
            boolean requiresModeration,
            String returnTypeSignature,
            Optional<Integer> overrideChatModelParamPosition,
            Optional<MetricsTimedInfo> metricsTimedInfo,
            Optional<MetricsCountedInfo> metricsCountedInfo,
            Optional<SpanInfo> spanInfo,
            ResponseSchemaInfo responseSchemaInfo,
            Map<String, AnnotationLiteral<?>> toolClassInfo,
            List<String> mcpClientNames,
            boolean switchToWorkerThreadForToolExecution,
            String outputTokenAccumulatorClassName,
            String responseAugmenterClassName,
            InputGuardrailsLiteral inputGuardrails,
            OutputGuardrailsLiteral outputGuardrails) {
        this.interfaceName = interfaceName;
        this.methodName = methodName;
        this.systemMessageInfo = systemMessageInfo;
        this.userMessageInfo = userMessageInfo;
        this.memoryIdParamPosition = memoryIdParamPosition;
        this.requiresModeration = requiresModeration;
        this.returnTypeSignature = returnTypeSignature;
        this.overrideChatModelParamPosition = overrideChatModelParamPosition;
        this.returnTypeVal = new LazyValue<>(new Supplier<>() {
            @Override
            public Type get() {
                return TypeSignatureParser.parse(returnTypeSignature);
            }
        });
        this.metricsTimedInfo = metricsTimedInfo;
        this.metricsCountedInfo = metricsCountedInfo;
        this.spanInfo = spanInfo;
        this.responseSchemaInfo = responseSchemaInfo;
        this.toolClassInfo = toolClassInfo;
        this.mcpClientNames = mcpClientNames;
        this.inputGuardrails = inputGuardrails;
        this.outputGuardrails = outputGuardrails;
        this.outputTokenAccumulatorClassName = outputTokenAccumulatorClassName;
        // Use a lazy value to get the value at runtime.
        this.quarkusGuardrailsMaxRetry = new LazyValue<Integer>(new Supplier<Integer>() {
            @Override
            public Integer get() {
                return ConfigProvider.getConfig().getOptionalValue("quarkus.langchain4j.guardrails.max-retries", Integer.class)
                        .orElse(GuardrailsConfig.MAX_RETRIES_DEFAULT);
            }
        });
        this.switchToWorkerThreadForToolExecution = switchToWorkerThreadForToolExecution;
        this.responseAugmenterClassName = responseAugmenterClassName;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public String getMethodName() {
        return methodName;
    }

    public Optional<TemplateInfo> getSystemMessageInfo() {
        return systemMessageInfo;
    }

    public UserMessageInfo getUserMessageInfo() {
        return userMessageInfo;
    }

    public Optional<Integer> getMemoryIdParamPosition() {
        return memoryIdParamPosition;
    }

    public boolean isRequiresModeration() {
        return requiresModeration;
    }

    public String getReturnTypeSignature() {
        return returnTypeSignature;
    }

    public Optional<Integer> getOverrideChatModelParamPosition() {
        return overrideChatModelParamPosition;
    }

    public Type getReturnType() {
        return returnTypeVal.get();
    }

    public Optional<MetricsTimedInfo> getMetricsTimedInfo() {
        return metricsTimedInfo;
    }

    public Optional<MetricsCountedInfo> getMetricsCountedInfo() {
        return metricsCountedInfo;
    }

    public Optional<SpanInfo> getSpanInfo() {
        return spanInfo;
    }

    public ResponseSchemaInfo getResponseSchemaInfo() {
        return responseSchemaInfo;
    }

    public Map<String, AnnotationLiteral<?>> getToolClassInfo() {
        return toolClassInfo;
    }

    public List<String> getMcpClientNames() {
        return mcpClientNames;
    }

    public List<ToolSpecification> getToolSpecifications() {
        return toolSpecifications;
    }

    public Map<String, ToolExecutor> getToolExecutors() {
        return toolExecutors;
    }

    public InputGuardrailsLiteral getInputGuardrails() {
        return inputGuardrails;
    }

    public String getResponseAugmenterClassName() {
        return responseAugmenterClassName;
    }

    @SuppressWarnings("unchecked")
    public Class<? extends AiResponseAugmenter<?>> getResponseAugmenter() {
        if (this.responseAugmenterClassName == null) {
            return null;
        }

        synchronized (this) {
            if (this.augmenter == null) { // Not loaded yet.
                try {
                    this.augmenter = (Class<? extends AiResponseAugmenter<?>>) Class.forName(
                            getResponseAugmenterClassName(), true,
                            Thread.currentThread().getContextClassLoader());
                } catch (Exception e) {
                    throw new RuntimeException(
                            "Could not find " + AiResponseAugmenter.class.getSimpleName() + " implementation class: "
                                    + getResponseAugmenterClassName(),
                            e);
                }
            }
            return augmenter;
        }
    }

    public int getQuarkusGuardrailsMaxRetry() {
        return quarkusGuardrailsMaxRetry.get();
    }

    public OutputGuardrailsLiteral getOutputGuardrails() {
        return outputGuardrails;
    }

    public String getOutputTokenAccumulatorClassName() {
        return outputTokenAccumulatorClassName;
    }

    public void setOutputTokenAccumulator(OutputTokenAccumulator accumulator) {
        this.accumulator = accumulator;
    }

    public OutputTokenAccumulator getOutputTokenAccumulator() {
        return accumulator;
    }

    public String getUserMessageTemplate() {
        Optional<String> userMessageTemplateOpt = this.getUserMessageInfo().template()
                .flatMap(AiServiceMethodCreateInfo.TemplateInfo::text);

        return userMessageTemplateOpt.orElse("");
    }

    public boolean isSwitchToWorkerThreadForToolExecution() {
        return switchToWorkerThreadForToolExecution;
    }

    public void setResponseAugmenter(Class<? extends AiResponseAugmenter<?>> augmenter) {
        this.augmenter = augmenter;
    }

    public record UserMessageInfo(Optional<TemplateInfo> template,
            Optional<Integer> paramPosition,
            Optional<Integer> userNameParamPosition,
            Optional<Integer> imageParamPosition,
            Optional<Integer> audioParamPosition,
            Optional<Integer> pdfParamPosition,
            Optional<Integer> videoParamPosition) {

        public static UserMessageInfo fromMethodParam(int paramPosition, Optional<Integer> userNameParamPosition,
                Optional<Integer> imageParamPosition, Optional<Integer> audioParamPosition,
                Optional<Integer> pdfParamPosition,
                Optional<Integer> videoParamPosition) {
            return new UserMessageInfo(Optional.empty(), Optional.of(paramPosition),
                    userNameParamPosition, imageParamPosition, audioParamPosition, pdfParamPosition, videoParamPosition);
        }

        public static UserMessageInfo fromTemplate(TemplateInfo templateInfo, Optional<Integer> userNameParamPosition,
                Optional<Integer> imageUrlParamPosition,
                Optional<Integer> audioParamPosition,
                Optional<Integer> pdfParamPosition,
                Optional<Integer> videoParamPosition) {
            return new UserMessageInfo(Optional.of(templateInfo), Optional.empty(), userNameParamPosition,
                    imageUrlParamPosition, audioParamPosition, pdfParamPosition, videoParamPosition);
        }
    }

    /**
     * @param methodParamPosition this is used to determine the position of the parameter that holds the template, and it is
     *        never set if 'text' is set
     */
    public record TemplateInfo(Optional<String> text, Map<String, Integer> nameToParamPosition,
            Optional<Integer> methodParamPosition) {

        public static TemplateInfo fromText(String text, Map<String, Integer> nameToParamPosition) {
            return new TemplateInfo(Optional.of(text), nameToParamPosition, Optional.empty());
        }

        public static TemplateInfo fromMethodParam(Integer methodParamPosition,
                Map<String, Integer> nameToParamPosition) {
            return new TemplateInfo(Optional.empty(), nameToParamPosition, Optional.of(methodParamPosition));
        }
    }

    public record MetricsTimedInfo(String name,
            boolean longTask,
            String[] extraTags,
            double[] percentiles,
            boolean histogram, String description) {

        public static class Builder {
            private final String name;
            private boolean longTask = false;
            private String[] extraTags = {};
            private double[] percentiles = {};
            private boolean histogram = false;
            private String description = "";

            public Builder(String name) {
                this.name = name;
            }

            public Builder setLongTask(boolean longTask) {
                this.longTask = longTask;
                return this;
            }

            public Builder setExtraTags(String[] extraTags) {
                this.extraTags = extraTags;
                return this;
            }

            public Builder setPercentiles(double[] percentiles) {
                this.percentiles = percentiles;
                return this;
            }

            public Builder setHistogram(boolean histogram) {
                this.histogram = histogram;
                return this;
            }

            public Builder setDescription(String description) {
                this.description = description;
                return this;
            }

            public MetricsTimedInfo build() {
                return new MetricsTimedInfo(name, longTask, extraTags, percentiles, histogram,
                        description);
            }
        }
    }

    public record MetricsCountedInfo(String name,
            String[] extraTags,
            boolean recordFailuresOnly,
            String description) {

        public static class Builder {
            private final String name;
            private String[] extraTags = {};
            private boolean recordFailuresOnly = false;
            private String description = "";

            public Builder(String name) {
                this.name = name;
            }

            public Builder setExtraTags(String[] extraTags) {
                this.extraTags = extraTags;
                return this;
            }

            public Builder setRecordFailuresOnly(boolean recordFailuresOnly) {
                this.recordFailuresOnly = recordFailuresOnly;
                return this;
            }

            public Builder setDescription(String description) {
                this.description = description;
                return this;
            }

            public MetricsCountedInfo build() {
                return new MetricsCountedInfo(name, extraTags, recordFailuresOnly, description);
            }
        }
    }

    public record SpanInfo(String name) {
    }

    public record ResponseSchemaInfo(boolean enabled, boolean isInSystemMessage, Optional<Boolean> isInUserMessage,
            String outputFormatInstructions, Optional<JsonSchema> structuredOutputSchema) {

        public static ResponseSchemaInfo of(boolean enabled, Optional<TemplateInfo> systemMessageInfo,
                Optional<TemplateInfo> userMessageInfo,
                String outputFormatInstructions,
                Optional<JsonSchema> structuredOutputSchema) {

            boolean systemMessage = systemMessageInfo.flatMap(TemplateInfo::text)
                    .map(text -> text.contains(ResponseSchemaUtil.placeholder()))
                    .orElse(false);

            Optional<Boolean> userMessage = Optional.empty();
            if (userMessageInfo.isPresent() && userMessageInfo.get().text.isPresent()) {
                userMessage = Optional.of(userMessageInfo.get().text.get().contains(ResponseSchemaUtil.placeholder()));
            }

            return new ResponseSchemaInfo(enabled, systemMessage, userMessage, outputFormatInstructions,
                    structuredOutputSchema);
        }
    }
}
