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

    private final Optional<MetricsInfo> metricsInfo;

    private final Optional<SpanInfo> spanInfo;

    @RecordableConstructor
    public AiServiceMethodCreateInfo(String interfaceName, String methodName,
            Optional<TemplateInfo> systemMessageInfo, UserMessageInfo userMessageInfo,
            Optional<Integer> memoryIdParamPosition,
            boolean requiresModeration, Class<?> returnType,
            Optional<MetricsInfo> metricsInfo,
            Optional<SpanInfo> spanInfo) {
        this.interfaceName = interfaceName;
        this.methodName = methodName;
        this.systemMessageInfo = systemMessageInfo;
        this.userMessageInfo = userMessageInfo;
        this.memoryIdParamPosition = memoryIdParamPosition;
        this.requiresModeration = requiresModeration;
        this.returnType = returnType;
        this.metricsInfo = metricsInfo;
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

    public Optional<MetricsInfo> getMetricsInfo() {
        return metricsInfo;
    }

    public Optional<SpanInfo> getSpanInfo() {
        return spanInfo;
    }

    public static class UserMessageInfo {
        private final Optional<TemplateInfo> template;
        private final Optional<Integer> paramPosition;
        private final Optional<String> instructions;
        private final Optional<Integer> userNameParamPosition;

        @RecordableConstructor
        public UserMessageInfo(Optional<TemplateInfo> template, Optional<Integer> paramPosition, Optional<String> instructions,
                Optional<Integer> userNameParamPosition) {
            this.template = template;
            this.paramPosition = paramPosition;
            this.instructions = instructions;
            this.userNameParamPosition = userNameParamPosition;
        }

        public static UserMessageInfo fromMethodParam(int paramPosition, String instructions,
                Optional<Integer> userNameParamPosition) {
            return new UserMessageInfo(Optional.empty(), Optional.of(paramPosition), Optional.of(instructions),
                    userNameParamPosition);
        }

        public static UserMessageInfo fromTemplate(TemplateInfo templateInfo, Optional<Integer> userNameParamPosition) {
            return new UserMessageInfo(Optional.of(templateInfo), Optional.empty(), Optional.empty(), userNameParamPosition);
        }

        public Optional<TemplateInfo> getTemplate() {
            return template;
        }

        public Optional<Integer> getParamPosition() {
            return paramPosition;
        }

        public Optional<String> getInstructions() {
            return instructions;
        }

        public Optional<Integer> getUserNameParamPosition() {
            return userNameParamPosition;
        }
    }

    public static class TemplateInfo {

        private final String text;
        private final Map<String, Integer> nameToParamPosition;

        @RecordableConstructor
        public TemplateInfo(String text, Map<String, Integer> nameToParamPosition) {
            this.text = text;
            this.nameToParamPosition = nameToParamPosition;
        }

        public String getText() {
            return text;
        }

        public Map<String, Integer> getNameToParamPosition() {
            return nameToParamPosition;
        }
    }

    public static class MetricsInfo {
        private final String name;
        private final boolean longTask;
        private final String[] extraTags;
        private final double[] percentiles;
        private final boolean histogram;
        private final String description;

        @RecordableConstructor
        public MetricsInfo(String name, boolean longTask, String[] extraTags, double[] percentiles, boolean histogram,
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

            public AiServiceMethodCreateInfo.MetricsInfo build() {
                return new AiServiceMethodCreateInfo.MetricsInfo(name, longTask, extraTags, percentiles, histogram,
                        description);
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
