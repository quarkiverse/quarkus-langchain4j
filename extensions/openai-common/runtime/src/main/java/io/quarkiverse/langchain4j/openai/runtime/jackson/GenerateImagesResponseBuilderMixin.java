package io.quarkiverse.langchain4j.openai.runtime.jackson;

import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import dev.ai4j.openai4j.image.GenerateImagesResponse;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(GenerateImagesResponse.Builder.class)
@JsonPOJOBuilder(withPrefix = "")
public abstract class GenerateImagesResponseBuilderMixin {
}
