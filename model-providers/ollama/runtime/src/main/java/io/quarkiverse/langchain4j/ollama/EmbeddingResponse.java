package io.quarkiverse.langchain4j.ollama;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

@JsonDeserialize(builder = EmbeddingResponse.Builder.class)
public class EmbeddingResponse {

    private float[][] embeddings;

    private Integer promptEvalCount;

    private EmbeddingResponse(Builder builder) {
        embeddings = builder.embeddings;
        promptEvalCount = builder.promptEvalCount;
    }

    public float[][] getEmbeddings() {
        return embeddings;
    }

    public void setEmbeddings(float[][] embeddings) {
        this.embeddings = embeddings;
    }

    public Integer getPromptEvalCount() {
        return promptEvalCount;
    }

    public void setPromptEvalCount(Integer promptEvalCount) {
        this.promptEvalCount = promptEvalCount;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {
        private float[][] embeddings;
        private Integer promptEvalCount;

        private Builder() {
        }

        public Builder embeddings(float[][] val) {
            embeddings = val;
            return this;
        }

        public Builder promptEvalCount(Integer val) {
            promptEvalCount = val;
            return this;
        }

        public EmbeddingResponse build() {
            return new EmbeddingResponse(this);
        }
    }

}
