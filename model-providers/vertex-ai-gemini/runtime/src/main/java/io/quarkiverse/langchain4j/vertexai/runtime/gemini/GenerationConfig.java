package io.quarkiverse.langchain4j.vertexai.runtime.gemini;

public class GenerationConfig {

    private final Double temperature;
    private final Integer maxOutputTokens;
    private final Integer topK;
    private final Double topP;

    public GenerationConfig(Builder builder) {
        this.temperature = builder.temperature;
        this.maxOutputTokens = builder.maxOutputTokens;
        this.topK = builder.topK;
        this.topP = builder.topP;
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

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private Double temperature;
        private Integer maxOutputTokens;
        private Integer topK;
        private Double topP;

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

        public GenerationConfig build() {
            return new GenerationConfig(this);
        }
    }
}
