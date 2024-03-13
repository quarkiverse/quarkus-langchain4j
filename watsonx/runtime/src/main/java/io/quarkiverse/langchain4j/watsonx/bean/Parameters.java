package io.quarkiverse.langchain4j.watsonx.bean;

import java.util.List;

public class Parameters {

    private final String decodingMethod;
    private final Integer minNewTokens;
    private final Integer maxNewTokens;
    private final Integer randomSeed;
    private final List<String> stopSequences;
    private final Double temperature;
    private final Integer topK;
    private final Double topP;
    private final Double repetitionPenalty;

    private Parameters(Builder builder) {
        this.decodingMethod = builder.decodingMethod;
        this.minNewTokens = builder.minNewTokens;
        this.maxNewTokens = builder.maxNewTokens;
        this.randomSeed = builder.randomSeed;
        this.stopSequences = builder.stopSequences;
        this.temperature = builder.temperature;
        this.topK = builder.topK;
        this.topP = builder.topP;
        this.repetitionPenalty = builder.repetitionPenalty;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getDecodingMethod() {
        return decodingMethod;
    }

    public Integer getMinNewTokens() {
        return minNewTokens;
    }

    public Integer getMaxNewTokens() {
        return maxNewTokens;
    }

    public Double getTemperature() {
        return temperature;
    }

    public Integer getRandomSeed() {
        return randomSeed;
    }

    public List<String> getStopSequences() {
        return stopSequences;
    }

    public Integer getTopK() {
        return topK;
    }

    public Double getTopP() {
        return topP;
    }

    public Double getRepetitionPenalty() {
        return repetitionPenalty;
    }

    public static class Builder {

        private String decodingMethod;
        private Integer minNewTokens;
        private Integer maxNewTokens;
        private Integer randomSeed;
        private List<String> stopSequences;
        private Double temperature;
        private Integer topK;
        private Double topP;
        private Double repetitionPenalty;

        public Builder decodingMethod(String decodingMethod) {
            this.decodingMethod = decodingMethod;
            return this;
        }

        public Builder minNewTokens(Integer minNewTokens) {
            this.minNewTokens = minNewTokens;
            return this;
        }

        public Builder maxNewTokens(Integer maxNewTokens) {
            this.maxNewTokens = maxNewTokens;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder decondingMethod(String decodingMethod) {
            this.decodingMethod = decodingMethod;
            return this;
        }

        public Builder randomSeed(Integer randomSeed) {
            this.randomSeed = randomSeed;
            return this;
        }

        public Builder repetitionPenalty(Double repetitionPenalty) {
            this.repetitionPenalty = repetitionPenalty;
            return this;
        }

        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public Builder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public Builder stopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return this;
        }

        public Parameters build() {
            return new Parameters(this);
        }
    }
}
