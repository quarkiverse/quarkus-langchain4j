package io.quarkiverse.langchain4j.watsonx;

import static dev.langchain4j.internal.Utils.getOrDefault;

import java.time.Duration;

import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import io.quarkiverse.langchain4j.watsonx.bean.TextGenerationParameters.LengthPenalty;

public class WatsonxGenerationRequestParameters extends DefaultChatRequestParameters {

    private String decodingMethod;
    private LengthPenalty lengthPenalty;
    private Integer minNewTokens;
    private Integer randomSeed;
    private Duration timeLimit;
    private Double repetitionPenalty;
    private Integer truncateInputTokens;
    private Boolean includeStopSequence;

    private WatsonxGenerationRequestParameters(Builder builder) {
        super(builder);
        this.decodingMethod = builder.decodingMethod;
        this.lengthPenalty = builder.lengthPenalty;
        this.minNewTokens = builder.minNewTokens;
        this.randomSeed = builder.randomSeed;
        this.timeLimit = builder.timeLimit;
        this.repetitionPenalty = builder.repetitionPenalty;
        this.truncateInputTokens = builder.truncateInputTokens;
        this.includeStopSequence = builder.includeStopSequence;
    }

    public String decodingMethod() {
        return decodingMethod;
    }

    public LengthPenalty lengthPenalty() {
        return lengthPenalty;
    }

    public Integer minNewTokens() {
        return minNewTokens;
    }

    public Integer randomSeed() {
        return randomSeed;
    }

    public Duration timeLimit() {
        return timeLimit;
    }

    public Double repetitionPenalty() {
        return repetitionPenalty;
    }

    public Integer truncateInputTokens() {
        return truncateInputTokens;
    }

    public Boolean includeStopSequence() {
        return includeStopSequence;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ChatRequestParameters overrideWith(ChatRequestParameters that) {
        return WatsonxGenerationRequestParameters.builder()
                .overrideWith(this)
                .overrideWith(that)
                .build();
    }

    public static class Builder extends DefaultChatRequestParameters.Builder<Builder> {

        private String decodingMethod;
        private LengthPenalty lengthPenalty;
        private Integer minNewTokens;
        private Integer randomSeed;
        private Duration timeLimit;
        private Double repetitionPenalty;
        private Integer truncateInputTokens;
        private Boolean includeStopSequence;

        @Override
        public Builder overrideWith(ChatRequestParameters parameters) {
            parameters = parameters == null ? WatsonxGenerationRequestParameters.builder().build() : parameters;
            super.overrideWith(parameters);
            if (parameters instanceof WatsonxGenerationRequestParameters watsonxParameters) {
                decodingMethod(getOrDefault(watsonxParameters.decodingMethod(), decodingMethod));
                lengthPenalty(getOrDefault(watsonxParameters.lengthPenalty(), lengthPenalty));
                minNewTokens(getOrDefault(watsonxParameters.minNewTokens(), minNewTokens));
                randomSeed(getOrDefault(watsonxParameters.randomSeed(), randomSeed));
                timeLimit(getOrDefault(watsonxParameters.timeLimit(), timeLimit));
                repetitionPenalty(getOrDefault(watsonxParameters.repetitionPenalty(), repetitionPenalty));
                truncateInputTokens(getOrDefault(watsonxParameters.truncateInputTokens(), truncateInputTokens));
                includeStopSequence(getOrDefault(watsonxParameters.includeStopSequence(), includeStopSequence));
            }

            return this;
        }

        public Builder decodingMethod(String decodingMethod) {
            this.decodingMethod = decodingMethod;
            return this;
        }

        public Builder lengthPenalty(LengthPenalty lengthPenalty) {
            this.lengthPenalty = lengthPenalty;
            return this;
        }

        public Builder minNewTokens(Integer minNewTokens) {
            this.minNewTokens = minNewTokens;
            return this;
        }

        public Builder randomSeed(Integer randomSeed) {
            this.randomSeed = randomSeed;
            return this;
        }

        public Builder timeLimit(Duration timeLimit) {
            this.timeLimit = timeLimit;
            return this;
        }

        public Builder repetitionPenalty(Double repetitionPenalty) {
            this.repetitionPenalty = repetitionPenalty;
            return this;
        }

        public Builder truncateInputTokens(Integer truncateInputTokens) {
            this.truncateInputTokens = truncateInputTokens;
            return this;
        }

        public Builder includeStopSequence(Boolean includeStopSequence) {
            this.includeStopSequence = includeStopSequence;
            return this;
        }

        public WatsonxGenerationRequestParameters build() {
            return new WatsonxGenerationRequestParameters(this);
        }
    }
}
