package io.quarkiverse.langchain4j.runtime.jackson;

import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import dev.ai4j.openai4j.embedding.Embedding;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(Embedding.Builder.class)
@JsonPOJOBuilder(withPrefix = "")
public abstract class EmbeddingBuilderMixin {
}
