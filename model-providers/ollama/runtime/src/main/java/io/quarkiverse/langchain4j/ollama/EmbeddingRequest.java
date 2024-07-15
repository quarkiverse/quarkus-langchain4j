package io.quarkiverse.langchain4j.ollama;

public class EmbeddingRequest {

    private final String model;
    private final String prompt;

    private EmbeddingRequest(Builder builder) {
        model = builder.model;
        prompt = builder.prompt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getModel() {
        return model;
    }

    public String getPrompt() {
        return prompt;
    }

    public static final class Builder {
        private String model = "llama2";
        private String prompt;

        private Builder() {
        }

        public Builder model(String val) {
            model = val;
            return this;
        }

        public Builder prompt(String val) {
            prompt = val;
            return this;
        }

        public EmbeddingRequest build() {
            return new EmbeddingRequest(this);
        }
    }
}
