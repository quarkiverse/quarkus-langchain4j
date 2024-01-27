package io.quarkiverse.langchain4j.openai.runtime.jackson;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import dev.ai4j.openai4j.image.GenerateImagesResponse;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(GenerateImagesResponse.class)
@JsonDeserialize(builder = GenerateImagesResponse.Builder.class)
public abstract class GenerateImagesResponseMixin {
}
