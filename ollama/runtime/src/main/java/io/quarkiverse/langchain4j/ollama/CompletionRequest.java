package io.quarkiverse.langchain4j.ollama;

public class CompletionRequest {

    private final String model;
    private final String prompt;
    private final Options options;
    private final Boolean stream;

    private CompletionRequest(Builder builder) {
        model = builder.model;
        prompt = builder.prompt;
        options = builder.options;
        stream = builder.stream;
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

    public Options getOptions() {
        return options;
    }

    public Boolean getStream() {
        return stream;
    }

    public static final class Builder {
        private String model = "llama2";
        private String prompt;
        private Options options;
        private Boolean stream = false;

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

        public Builder options(Options val) {
            options = val;
            return this;
        }

        public Builder stream(Boolean val) {
            stream = val;
            return this;
        }

        public CompletionRequest build() {
            return new CompletionRequest(this);
        }
    }
}
