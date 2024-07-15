package io.quarkiverse.langchain4j.ollama;

import java.util.List;

/**
 * request options in completion/embedding API
 *
 * @see <a href="https://github.com/jmorganca/ollama/blob/main/docs/modelfile.md#valid-parameters-and-values">Ollama REST API
 *      Doc</a>
 */
public record Options(Double temperature, Integer topK, Double topP, Double repeatPenalty, Integer seed, Integer numPredict,
        Integer numCtx, List<String> stop) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Double temperature;
        private Integer topK;
        private Double topP;
        private Double repeatPenalty;
        private Integer seed;
        private Integer numPredict;
        private Integer numCtx;
        private List<String> stop;

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
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

        public Builder repeatPenalty(Double repeatPenalty) {
            this.repeatPenalty = repeatPenalty;
            return this;
        }

        public Builder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public Builder numPredict(Integer numPredict) {
            this.numPredict = numPredict;
            return this;
        }

        public Builder numCtx(Integer numCtx) {
            this.numCtx = numCtx;
            return this;
        }

        public Builder stop(List<String> stop) {
            this.stop = stop;
            return this;
        }

        public Options build() {
            return new Options(temperature, topK, topP, repeatPenalty, seed, numPredict, numCtx, stop);
        }
    }

}
