package io.quarkiverse.langchain4j.runtime.jackson;

import dev.langchain4j.data.image.Image;
import io.quarkus.jackson.JacksonMixin;
import tools.jackson.databind.annotation.JsonPOJOBuilder;

@JacksonMixin(Image.Builder.class)
@JsonPOJOBuilder(withPrefix = "")
public abstract class ImageBuilderMixin {
}
