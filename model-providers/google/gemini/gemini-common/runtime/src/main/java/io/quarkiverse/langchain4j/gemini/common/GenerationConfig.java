package io.quarkiverse.langchain4j.gemini.common;

import java.util.List;

public class GenerationConfig {

    private final Double temperature;
    private final Integer maxOutputTokens;
    private final Integer topK;
    private final Double topP;
    private final String responseMimeType;
    private final Schema responseSchema;
    private final List<String> stopSequences;
    private final ThinkingConfig thinkingConfig;

    public GenerationConfig(Builder builder) {
        this.temperature = builder.temperature;
        this.maxOutputTokens = builder.maxOutputTokens;
        this.topK = builder.topK;
        this.topP = builder.topP;
        this.responseMimeType = builder.responseMimeType;
        this.responseSchema = builder.responseSchema;
        this.stopSequences = builder.stopSequences;
        this.thinkingConfig = builder.thinkingConfig;
    }

    public Double getTemperature() {
        return temperature;
    }

    public Integer getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public Integer getTopK() {
        return topK;
    }

    public Double getTopP() {
        return topP;
    }

    public String getResponseMimeType() {
        return responseMimeType;
    }

    public Schema getResponseSchema() {
        return responseSchema;
    }

    public List<String> getStopSequences() {
        return stopSequences;
    }

    public ThinkingConfig getThinkingConfig() {
        return thinkingConfig;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private Double temperature;
        private Integer maxOutputTokens;
        private Integer topK;
        private Double topP;
        private String responseMimeType;
        private Schema responseSchema;
        private List<String> stopSequences;
        private ThinkingConfig thinkingConfig;

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        public Builder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public Builder responseMimeType(String responseMimeType) {
            this.responseMimeType = responseMimeType;
            return this;
        }

        public Builder responseSchema(Schema schema) {
            this.responseSchema = schema;
            return this;
        }

        public Builder stopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return this;
        }

        public Builder thinkingConfig(ThinkingConfig thinkingConfig) {
            this.thinkingConfig = thinkingConfig;
            return this;
        }

        public GenerationConfig build() {
            return new GenerationConfig(this);
        }
    }
}
