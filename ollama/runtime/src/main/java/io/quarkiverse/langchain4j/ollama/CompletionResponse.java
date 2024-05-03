package io.quarkiverse.langchain4j.ollama;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

@JsonDeserialize(builder = CompletionResponse.Builder.class)
public class CompletionResponse {

    private final String model;
    private final String createdAt;
    private final String response;
    private final Boolean done;
    private final Integer promptEvalCount;
    private final Integer evalCount;

    protected CompletionResponse(Builder builder) {
        model = builder.model;
        createdAt = builder.createdAt;
        response = builder.response;
        done = builder.done;
        promptEvalCount = builder.promptEvalCount;
        evalCount = builder.evalCount;
    }

    public String getModel() {
        return model;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getResponse() {
        return response;
    }

    public Boolean getDone() {
        return done;
    }

    public Integer getPromptEvalCount() {
        return promptEvalCount;
    }

    public Integer getEvalCount() {
        return evalCount;
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {
        private String model;
        private String createdAt;
        private String response;
        private Boolean done;
        private Integer promptEvalCount;
        private Integer evalCount;

        private Builder() {
        }

        public Builder model(String val) {
            model = val;
            return this;
        }

        public Builder createdAt(String val) {
            createdAt = val;
            return this;
        }

        public Builder response(String val) {
            response = val;
            return this;
        }

        public Builder done(Boolean val) {
            done = val;
            return this;
        }

        public Builder promptEvalCount(Integer val) {
            promptEvalCount = val;
            return this;
        }

        public Builder evalCount(Integer val) {
            evalCount = val;
            return this;
        }

        public CompletionResponse build() {
            return new CompletionResponse(this);
        }
    }
}
