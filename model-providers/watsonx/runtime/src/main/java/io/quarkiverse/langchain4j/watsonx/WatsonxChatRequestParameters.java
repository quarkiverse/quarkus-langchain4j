package io.quarkiverse.langchain4j.watsonx;

import static dev.langchain4j.internal.Utils.getOrDefault;

import java.time.Duration;
import java.util.Map;

import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;

public class WatsonxChatRequestParameters extends DefaultChatRequestParameters {

    private Map<String, Integer> logitBias;
    private Boolean logprobs;
    private Integer topLogprobs;
    private Integer n;
    private Integer seed;
    private String toolChoiceName;
    private Duration timeLimit;

    private WatsonxChatRequestParameters(Builder builder) {
        super(builder);
        this.logitBias = builder.logitBias;
        this.logprobs = builder.logprobs;
        this.topLogprobs = builder.topLogprobs;
        this.n = builder.n;
        this.seed = builder.seed;
        this.toolChoiceName = builder.toolChoiceName;
        this.timeLimit = builder.timeLimit;
    }

    public Map<String, Integer> logitBias() {
        return logitBias;
    }

    public Boolean logprobs() {
        return logprobs;
    }

    public Integer topLogprobs() {
        return topLogprobs;
    }

    public Integer n() {
        return n;
    }

    public Integer seed() {
        return seed;
    }

    public String toolChoiceName() {
        return toolChoiceName;
    }

    public Duration timeLimit() {
        return timeLimit;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ChatRequestParameters overrideWith(ChatRequestParameters that) {
        return WatsonxChatRequestParameters.builder()
                .overrideWith(this)
                .overrideWith(that)
                .build();
    }

    public static class Builder extends DefaultChatRequestParameters.Builder<Builder> {

        private Map<String, Integer> logitBias;
        private Boolean logprobs;
        private Integer topLogprobs;
        private Integer n;
        private Integer seed;
        private String toolChoiceName;
        private Duration timeLimit;

        @Override
        public Builder overrideWith(ChatRequestParameters parameters) {
            parameters = parameters == null ? WatsonxChatRequestParameters.builder().build() : parameters;
            super.overrideWith(parameters);
            if (parameters instanceof WatsonxChatRequestParameters watsonxParameters) {
                logitBias(getOrDefault(watsonxParameters.logitBias(), logitBias));
                logprobs(getOrDefault(watsonxParameters.logprobs(), logprobs));
                topLogprobs(getOrDefault(watsonxParameters.topLogprobs(), topLogprobs));
                n(getOrDefault(watsonxParameters.n(), n));
                seed(getOrDefault(watsonxParameters.seed(), seed));
                toolChoiceName(getOrDefault(watsonxParameters.toolChoiceName(), toolChoiceName));
                timeLimit(getOrDefault(watsonxParameters.timeLimit(), timeLimit));
            }
            return this;
        }

        public Builder logitBias(Map<String, Integer> logitBias) {
            this.logitBias = logitBias;
            return this;
        }

        public Builder logprobs(Boolean logprobs) {
            this.logprobs = logprobs;
            return this;
        }

        public Builder topLogprobs(Integer topLogprobs) {
            this.topLogprobs = topLogprobs;
            return this;
        }

        public Builder n(Integer n) {
            this.n = n;
            return this;
        }

        public Builder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public Builder toolChoiceName(String toolChoiceName) {
            this.toolChoiceName = toolChoiceName;
            return this;
        }

        public Builder timeLimit(Duration timeLimit) {
            this.timeLimit = timeLimit;
            return this;
        }

        public WatsonxChatRequestParameters build() {
            return new WatsonxChatRequestParameters(this);
        }
    }
}
