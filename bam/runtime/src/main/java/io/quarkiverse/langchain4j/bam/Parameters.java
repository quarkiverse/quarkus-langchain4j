package io.quarkiverse.langchain4j.bam;

public class Parameters {

    private final String decodingMethod;
    private final Integer minNewTokens;
    private final Integer maxNewTokens;
    private final Double temperature;

    private Parameters(Builder builder) {
        this.decodingMethod = builder.decodingMethod;
        this.minNewTokens = builder.minNewTokens;
        this.maxNewTokens = builder.maxNewTokens;
        this.temperature = builder.temperature;
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

    public static class Builder {
        private String decodingMethod;
        private Integer minNewTokens;
        private Integer maxNewTokens;
        private Double temperature;

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

        public Parameters build() {
            return new Parameters(this);
        }
    }
}
