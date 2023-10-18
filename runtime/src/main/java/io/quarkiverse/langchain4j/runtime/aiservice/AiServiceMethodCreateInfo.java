package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.RecordableConstructor;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class AiServiceMethodCreateInfo {

    private final Optional<TemplateInfo> systemMessageInfo;
    private final UserMessageInfo userMessageInfo;
    private final Optional<Integer> memoryIdParamPosition;

    private final boolean requiresModeration;
    private final Class<?> returnType;

    @RecordableConstructor
    public AiServiceMethodCreateInfo(Optional<TemplateInfo> systemMessageInfo, UserMessageInfo userMessageInfo,
            Optional<Integer> memoryIdParamPosition,
            boolean requiresModeration, Class<?> returnType) {
        this.systemMessageInfo = systemMessageInfo;
        this.userMessageInfo = userMessageInfo;
        this.memoryIdParamPosition = memoryIdParamPosition;
        this.requiresModeration = requiresModeration;
        this.returnType = returnType;
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
}
