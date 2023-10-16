package io.quarkiverse.langchain4j.runtime.jackson;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import dev.ai4j.openai4j.embedding.Embedding;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(Embedding.class)
@JsonDeserialize(builder = Embedding.Builder.class)
public abstract class EmbeddingMixin {
}
