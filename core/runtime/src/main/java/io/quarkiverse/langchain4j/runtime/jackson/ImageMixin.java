package io.quarkiverse.langchain4j.runtime.jackson;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import dev.langchain4j.data.image.Image;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(Image.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonDeserialize(builder = Image.Builder.class)
public abstract class ImageMixin {

}
