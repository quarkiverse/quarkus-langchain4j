package io.quarkiverse.langchain4j.ollama;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;

public record Message(Role role, String content, List<ToolCall> toolCalls, List<String> images,
        @JsonIgnore Map<String, Object> additionalFields) {

    @JsonAnyGetter
    public Map<String, Object> getAdditionalFields() {
        return additionalFields;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Role role;
        private String content;
        private List<ToolCall> toolCalls;
        private List<String> images;
        private Map<String, Object> additionalFields;

        public Builder role(Role role) {
            this.role = role;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder toolCalls(List<ToolCall> toolCalls) {
            this.toolCalls = toolCalls;
            return this;
        }

        public Builder images(List<String> images) {
            this.images = images;
            return this;
        }

        @JsonAnySetter
        public Builder additionalFields(Map<String, Object> additionalFields) {
            this.additionalFields = additionalFields;
            return this;
        }

        public Message build() {
            return new Message(role, content, toolCalls, images, additionalFields);
        }
    }

}
