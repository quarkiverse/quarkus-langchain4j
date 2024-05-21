package io.quarkiverse.langchain4j.ollama;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

@JsonDeserialize(builder = EmbeddingResponse.Builder.class)
public class EmbeddingResponse {

    private float[] embedding;

    public EmbeddingResponse() {
    }

    private EmbeddingResponse(Builder builder) {
        embedding = builder.embedding;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {
        private float[] embedding;

        private Builder() {
        }

        public Builder embedding(float[] val) {
            embedding = val;
            return this;
        }

        public EmbeddingResponse build() {
            return new EmbeddingResponse(this);
        }
    }

}
