package io.quarkiverse.langchain4j.bam;

import java.util.List;

public class Parameters {

    private final String decodingMethod;
    private Boolean includeStopSequence;
    private final Integer minNewTokens;
    private final Integer maxNewTokens;
    private Integer randomSeed;
    private List<String> stopSequences;
    private final Double temperature;
    private Integer timeLimit;
    private Integer topK;
    private Double topP;
    private Double typicalP;
    private Double repetitionPenalty;
    private Integer truncateInputTokens;
    private Integer beamWidth;

    private Parameters(Builder builder) {
        this.decodingMethod = builder.decodingMethod;
        this.includeStopSequence = builder.includeStopSequence;
        this.minNewTokens = builder.minNewTokens;
        this.maxNewTokens = builder.maxNewTokens;
        this.randomSeed = builder.randomSeed;
        this.stopSequences = builder.stopSequences;
        this.temperature = builder.temperature;
        this.timeLimit = builder.timeLimit;
        this.topK = builder.topK;
        this.topP = builder.topP;
        this.typicalP = builder.typicalP;
        this.repetitionPenalty = builder.repetitionPenalty;
        this.truncateInputTokens = builder.truncateInputTokens;
        this.beamWidth = builder.beamWidth;
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

    public Boolean getIncludeStopSequence() {
        return includeStopSequence;
    }

    public Integer getRandomSeed() {
        return randomSeed;
    }

    public List<String> getStopSequences() {
        return stopSequences;
    }

    public Integer getTimeLimit() {
        return timeLimit;
    }

    public Integer getTopK() {
        return topK;
    }

    public Double getTopP() {
        return topP;
    }

    public Double getTypicalP() {
        return typicalP;
    }

    public Double getRepetitionPenalty() {
        return repetitionPenalty;
    }

    public Integer getTruncateInputTokens() {
        return truncateInputTokens;
    }

    public Integer getBeamWidth() {
        return beamWidth;
    }

    public static class Builder {

        private String decodingMethod;
        private Boolean includeStopSequence;
        private Integer minNewTokens;
        private Integer maxNewTokens;
        private Integer randomSeed;
        private List<String> stopSequences;
        private Double temperature;
        private Integer timeLimit;
        private Integer topK;
        private Double topP;
        private Double typicalP;
        private Double repetitionPenalty;
        private Integer truncateInputTokens;
        private Integer beamWidth;

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

        public Builder includeStopSequence(Boolean includeStopSequence) {
            this.includeStopSequence = includeStopSequence;
            return this;
        }

        public Builder randomSeed(Integer randomSeed) {
            this.randomSeed = randomSeed;
            return this;
        }

        public Builder typicalP(Double typicalP) {
            this.typicalP = typicalP;
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

        public Builder beamWidth(Integer beamWidth) {
            this.beamWidth = beamWidth;
            return this;
        }

        public Builder timeLimit(Integer timeLimit) {
            this.timeLimit = timeLimit;
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
