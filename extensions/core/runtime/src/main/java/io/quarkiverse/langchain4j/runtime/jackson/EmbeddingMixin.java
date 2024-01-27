package io.quarkiverse.langchain4j.runtime.jackson;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import dev.langchain4j.data.embedding.Embedding;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(Embedding.class)
public abstract class EmbeddingMixin {

    @JsonCreator
    public EmbeddingMixin(@JsonProperty("vector") float[] vector) {

    }
}
