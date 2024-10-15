package io.quarkiverse.langchain4j.watsonx.bean;

public class TextChatParameters {

    public record TextChatResponseFormat(String type) {
    };

    private final Integer maxTokens;
    private final Double temperature;
    private final Double topP;
    private final Long timeLimit;
    private final TextChatResponseFormat responseFormat;

    public TextChatParameters(Builder builder) {
        this.maxTokens = builder.maxTokens;
        this.temperature = builder.temperature;
        this.topP = builder.topP;
        this.timeLimit = builder.timeLimit;

        if (builder.responseFormat != null)
            this.responseFormat = new TextChatResponseFormat(builder.responseFormat);
        else
            this.responseFormat = null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public Double getTemperature() {
        return temperature;
    }

    public Double getTopP() {
        return topP;
    }

    public Long getTimeLimit() {
        return timeLimit;
    }

    public TextChatResponseFormat getResponseFormat() {
        return responseFormat;
    }

    public static class Builder {

        private Integer maxTokens;
        private Double temperature;
        private Double topP;
        private Long timeLimit;
        private String responseFormat;

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public Builder timeLimit(Long timeLimit) {
            this.timeLimit = timeLimit;
            return this;
        }

        public Builder responseFormat(String responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public TextChatParameters build() {
            return new TextChatParameters(this);
        }
    }
}
