package io.quarkiverse.langchain4j.runtime.jackson;

import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import dev.langchain4j.data.image.Image;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(Image.Builder.class)
@JsonPOJOBuilder(withPrefix = "")
public abstract class ImageBuilderMixin {
}
