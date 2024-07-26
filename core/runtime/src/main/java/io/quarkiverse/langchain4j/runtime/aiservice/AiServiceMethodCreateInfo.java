package io.quarkiverse.langchain4j.runtime.aiservice;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;
import io.quarkiverse.langchain4j.runtime.ResponseSchemaUtil;
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
    private final transient LazyValue<Type> returnTypeVal; // transient so bytecode recording ignores it
    private final Optional<MetricsTimedInfo> metricsTimedInfo;
    private final Optional<MetricsCountedInfo> metricsCountedInfo;
    private final Optional<SpanInfo> spanInfo;
    // support @Toolbox
    private final List<String> toolClassNames;
    private final ResponseSchemaInfo responseSchemaInfo;

    // these are populated when the AiService method is first called which can happen on any thread
    private transient final List<ToolSpecification> toolSpecifications = new CopyOnWriteArrayList<>();
    private transient final Map<String, ToolExecutor> toolExecutors = new ConcurrentHashMap<>();

    @RecordableConstructor
    public AiServiceMethodCreateInfo(String interfaceName, String methodName,
            Optional<TemplateInfo> systemMessageInfo,
            UserMessageInfo userMessageInfo,
            Optional<Integer> memoryIdParamPosition,
            boolean requiresModeration,
            String returnTypeSignature,
            Optional<MetricsTimedInfo> metricsTimedInfo,
            Optional<MetricsCountedInfo> metricsCountedInfo,
            Optional<SpanInfo> spanInfo,
            ResponseSchemaInfo responseSchemaInfo,
            List<String> toolClassNames) {
        this.interfaceName = interfaceName;
        this.methodName = methodName;
        this.systemMessageInfo = systemMessageInfo;
        this.userMessageInfo = userMessageInfo;
        this.memoryIdParamPosition = memoryIdParamPosition;
        this.requiresModeration = requiresModeration;
        this.returnTypeSignature = returnTypeSignature;
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
        this.toolClassNames = toolClassNames;
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

    public List<String> getToolClassNames() {
        return toolClassNames;
    }

    public List<ToolSpecification> getToolSpecifications() {
        return toolSpecifications;
    }

    public Map<String, ToolExecutor> getToolExecutors() {
        return toolExecutors;
    }

    public record UserMessageInfo(Optional<TemplateInfo> template,
            Optional<Integer> paramPosition,
            Optional<Integer> userNameParamPosition) {

        public static UserMessageInfo fromMethodParam(int paramPosition, Optional<Integer> userNameParamPosition) {
            return new UserMessageInfo(Optional.empty(), Optional.of(paramPosition),
                    userNameParamPosition);
        }

        public static UserMessageInfo fromTemplate(TemplateInfo templateInfo, Optional<Integer> userNameParamPosition) {
            return new UserMessageInfo(Optional.of(templateInfo), Optional.empty(), userNameParamPosition);
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
            String outputFormatInstructions) {

        public static ResponseSchemaInfo of(boolean enabled, Optional<TemplateInfo> systemMessageInfo,
                Optional<TemplateInfo> userMessageInfo,
                String outputFormatInstructions) {

            boolean systemMessage = systemMessageInfo.flatMap(TemplateInfo::text)
                    .map(text -> text.contains(ResponseSchemaUtil.placeholder()))
                    .orElse(false);

            Optional<Boolean> userMessage = Optional.empty();
            if (userMessageInfo.isPresent() && userMessageInfo.get().text.isPresent()) {
                userMessage = Optional.of(userMessageInfo.get().text.get().contains(ResponseSchemaUtil.placeholder()));
            }

            return new ResponseSchemaInfo(enabled, systemMessage, userMessage, outputFormatInstructions);
        }
    }
}
