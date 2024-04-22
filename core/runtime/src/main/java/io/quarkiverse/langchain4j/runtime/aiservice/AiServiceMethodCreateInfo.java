package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.RecordableConstructor;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class AiServiceMethodCreateInfo {

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

    @RecordableConstructor
    public AiServiceMethodCreateInfo(String interfaceName, String methodName,
            Optional<TemplateInfo> systemMessageInfo, UserMessageInfo userMessageInfo,
            Optional<Integer> memoryIdParamPosition,
            boolean requiresModeration, Class<?> returnType,
            Optional<MetricsTimedInfo> metricsTimedInfo,
            Optional<MetricsCountedInfo> metricsCountedInfo,
            Optional<SpanInfo> spanInfo) {
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

    public static class UserMessageInfo {
        private final Optional<TemplateInfo> template;
        private final Optional<Integer> paramPosition;
        private final Optional<Integer> userNameParamPosition;
        private final String outputFormatInstructions;

        @RecordableConstructor
        public UserMessageInfo(Optional<TemplateInfo> template, Optional<Integer> paramPosition,
                Optional<Integer> userNameParamPosition, String outputFormatInstructions) {
            this.template = template;
            this.paramPosition = paramPosition;
            this.userNameParamPosition = userNameParamPosition;
            this.outputFormatInstructions = outputFormatInstructions == null ? "" : outputFormatInstructions;
        }

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

        public Optional<TemplateInfo> getTemplate() {
            return template;
        }

        public Optional<Integer> getParamPosition() {
            return paramPosition;
        }

        public Optional<Integer> getUserNameParamPosition() {
            return userNameParamPosition;
        }

        public String getOutputFormatInstructions() {
            return outputFormatInstructions;
        }
    }

    public static class TemplateInfo {

        private final Optional<String> text;
        private final Map<String, Integer> nameToParamPosition;
        // this is used to determine the position of the parameter that holds the template,
        // and it is never set if 'text' is set
        private final Optional<Integer> methodParamPosition;

        @RecordableConstructor
        public TemplateInfo(Optional<String> text, Map<String, Integer> nameToParamPosition,
                Optional<Integer> methodParamPosition) {
            this.text = text;
            this.nameToParamPosition = nameToParamPosition;
            this.methodParamPosition = methodParamPosition;
        }

        public Optional<String> getText() {
            return text;
        }

        public Map<String, Integer> getNameToParamPosition() {
            return nameToParamPosition;
        }

        public Optional<Integer> getMethodParamPosition() {
            return methodParamPosition;
        }

        public static TemplateInfo fromText(String text, Map<String, Integer> nameToParamPosition) {
            return new TemplateInfo(Optional.of(text), nameToParamPosition, Optional.empty());
        }

        public static TemplateInfo fromMethodParam(Integer methodParamPosition, Map<String, Integer> nameToParamPosition) {
            return new TemplateInfo(Optional.empty(), nameToParamPosition, Optional.of(methodParamPosition));
        }
    }

    public static class MetricsTimedInfo {
        private final String name;
        private final boolean longTask;
        private final String[] extraTags;
        private final double[] percentiles;
        private final boolean histogram;
        private final String description;

        @RecordableConstructor
        public MetricsTimedInfo(String name, boolean longTask, String[] extraTags, double[] percentiles, boolean histogram,
                String description) {
            this.name = name;
            this.longTask = longTask;
            this.extraTags = extraTags;
            this.percentiles = percentiles;
            this.histogram = histogram;
            this.description = description;
        }

        public String getName() {
            return name;
        }

        public boolean isLongTask() {
            return longTask;
        }

        public String[] getExtraTags() {
            return extraTags;
        }

        public double[] getPercentiles() {
            return percentiles;
        }

        public boolean isHistogram() {
            return histogram;
        }

        public String getDescription() {
            return description;
        }

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

    public static class MetricsCountedInfo {
        private final String name;
        private final boolean recordFailuresOnly;
        private final String[] extraTags;
        private final String description;

        @RecordableConstructor
        public MetricsCountedInfo(String name, String[] extraTags,
                boolean recordFailuresOnly, String description) {
            this.name = name;
            this.extraTags = extraTags;
            this.recordFailuresOnly = recordFailuresOnly;
            this.description = description;
        }

        public String getName() {
            return name;
        }

        public String[] getExtraTags() {
            return extraTags;
        }

        public boolean isRecordFailuresOnly() {
            return recordFailuresOnly;
        }

        public String getDescription() {
            return description;
        }

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

    public static class SpanInfo {
        private final String name;

        @RecordableConstructor
        public SpanInfo(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
