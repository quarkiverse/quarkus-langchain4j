package io.quarkiverse.langchain4j.ollama;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

@JsonDeserialize(builder = EmbeddingResponse.Builder.class)
public class EmbeddingResponse {

    private float[][] embeddings;

    private EmbeddingResponse(Builder builder) {
        embeddings = builder.embeddings;
    }

    public float[][] getEmbeddings() {
        return embeddings;
    }

    public void setEmbeddings(float[][] embeddings) {
        this.embeddings = embeddings;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {
        private float[][] embeddings;

        private Builder() {
        }

        public Builder embeddings(float[][] val) {
            embeddings = val;
            return this;
        }

        public EmbeddingResponse build() {
            return new EmbeddingResponse(this);
        }
    }

}
