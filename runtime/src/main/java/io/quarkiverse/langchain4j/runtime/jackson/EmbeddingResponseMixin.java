package io.quarkiverse.langchain4j.runtime.jackson;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import dev.ai4j.openai4j.embedding.EmbeddingResponse;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(EmbeddingResponse.class)
@JsonDeserialize(builder = EmbeddingResponse.Builder.class)
public abstract class EmbeddingResponseMixin {
}
