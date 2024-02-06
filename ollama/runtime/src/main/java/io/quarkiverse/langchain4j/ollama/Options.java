package io.quarkiverse.langchain4j.ollama;

/**
 * request options in completion/embedding API
 *
 * @see <a href="https://github.com/jmorganca/ollama/blob/main/docs/modelfile.md#valid-parameters-and-values">Ollama REST API
 *      Doc</a>
 */
public class Options {
    private final Double temperature;
    private final Integer numPredict;
    private final String stop;
    private final Double topP;
    private final Integer topK;
    private final Integer seed;

    private Options(Builder builder) {
        temperature = builder.temperature;
        numPredict = builder.numPredict;
        stop = builder.stop;
        topP = builder.topP;
        topK = builder.topK;
        seed = builder.seed;

    }

    public static Builder builder() {
        return new Builder();
    }

    public Double getTemperature() {
        return temperature;
    }

    public Integer getNumPredict() {
        return numPredict;
    }

    public String getStop() {
        return stop;
    }

    public Double getTopP() {
        return topP;
    }

    public Integer getTopK() {
        return topK;
    }

    public Integer getSeed() {
        return seed;
    }

    public static final class Builder {
        private Double temperature = 0.8;
        private Integer numPredict = 128;
        private String stop;
        private Double topP = 0.9;
        private Integer topK = 40;
        private Integer seed = 42;

        public Builder temperature(Double val) {
            temperature = val;
            return this;
        }

        public Builder numPredict(Integer numPredict) {
            this.numPredict = numPredict;
            return this;
        }

        public Builder stop(String stop) {
            this.stop = stop;
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

        public Builder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public Options build() {
            return new Options(this);
        }
    }
}
