package io.quarkiverse.langchain4j.ollama;

public class EmbeddingRequest {

    private final String model;
    private final String input;

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

    public String getInput() {
        return input;
    }

    public static final class Builder {
        private String model = "llama2";
        private String input;

        private Builder() {
        }

        public Builder model(String val) {
            model = val;
            return this;
        }

        public Builder input(String val) {
            input = val;
            return this;
        }

        public EmbeddingRequest build() {
            return new EmbeddingRequest(this);
        }
    }
}
