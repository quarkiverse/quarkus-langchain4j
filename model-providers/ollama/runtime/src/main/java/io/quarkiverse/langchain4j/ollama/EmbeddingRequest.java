package io.quarkiverse.langchain4j.ollama;

import java.util.List;

/**
 * Request body of the Ollama {@code POST /api/embed} endpoint, which embeds one or more inputs in a single call.
 */
public class EmbeddingRequest {

    private final String model;
    private final List<String> input;

    private EmbeddingRequest(Builder builder) {
        model = builder.model;
        input = builder.input;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getModel() {
        return model;
    }

    public List<String> getInput() {
        return input;
    }

    public static final class Builder {
        private String model = "llama2";
        private List<String> input;

        private Builder() {
        }

        public Builder model(String val) {
            model = val;
            return this;
        }

        public Builder input(List<String> val) {
            input = val;
            return this;
        }

        public EmbeddingRequest build() {
            return new EmbeddingRequest(this);
        }
    }
}
