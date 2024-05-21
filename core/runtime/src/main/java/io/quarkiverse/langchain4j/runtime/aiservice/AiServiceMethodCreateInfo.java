package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import dev.langchain4j.agent.tool.ToolExecutor;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.quarkus.runtime.annotations.RecordableConstructor;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public final class AiServiceMethodCreateInfo {
    private final String interfaceName;
    private final String methodName;
    private final Optional<TemplateInfo> systemMessageInfo;
    private final UserMessageInfo userMessageInfo;
    private final Optional<Integer> memoryIdParamPosition;
    private final boolean requiresModeration;
    private final Class<?> returnType;
    private final Optional<MetricsTimedInfo> metricsTimedInfo;
    private final Optional<MetricsCountedInfo> metricsCountedInfo;
    private final Optional<SpanInfo> spanInfo;
    // support @Toolbox
    private final List<String> toolClassNames;

    // these are populated when the AiService method is first called which can happen on any thread
    private transient final List<ToolSpecification> toolSpecifications = new CopyOnWriteArrayList<>();
    private transient final Map<String, ToolExecutor> toolExecutors = new ConcurrentHashMap<>();

    @RecordableConstructor
    public AiServiceMethodCreateInfo(String interfaceName, String methodName,
            Optional<TemplateInfo> systemMessageInfo,
            UserMessageInfo userMessageInfo,
            Optional<Integer> memoryIdParamPosition,
            boolean requiresModeration,
            Class<?> returnType,
            Optional<MetricsTimedInfo> metricsTimedInfo,
            Optional<MetricsCountedInfo> metricsCountedInfo,
            Optional<SpanInfo> spanInfo,
            List<String> toolClassNames) {
        this.interfaceName = interfaceName;
        this.methodName = methodName;
        this.systemMessageInfo = systemMessageInfo;
        this.userMessageInfo = userMessageInfo;
        this.memoryIdParamPosition = memoryIdParamPosition;
        this.requiresModeration = requiresModeration;
        this.returnType = returnType;
        this.metricsTimedInfo = metricsTimedInfo;
        this.metricsCountedInfo = metricsCountedInfo;
        this.spanInfo = spanInfo;
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

    public Class<?> getReturnType() {
        return returnType;
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
            Optional<Integer> userNameParamPosition,
            String outputFormatInstructions) {

        public static UserMessageInfo fromMethodParam(int paramPosition, Optional<Integer> userNameParamPosition,
                String outputFormatInstructions) {
            return new UserMessageInfo(Optional.empty(), Optional.of(paramPosition),
                    userNameParamPosition, outputFormatInstructions);
        }

        public static UserMessageInfo fromTemplate(TemplateInfo templateInfo, Optional<Integer> userNameParamPosition,
                String outputFormatInstructions) {
            return new UserMessageInfo(Optional.of(templateInfo), Optional.empty(), userNameParamPosition,
                    outputFormatInstructions);
        }
    }

    /**
     * @param methodParamPosition this is used to determine the position of the parameter that holds the template, and
     *        it is never set if 'text' is set
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
}
