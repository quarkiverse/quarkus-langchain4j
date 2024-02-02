package io.quarkiverse.langchain4j.runtime.jackson;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.ImageContent;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(ImageContent.class)
public abstract class ImageContentMixin {

    @JsonCreator
    public ImageContentMixin(@JsonProperty("image") Image image,
            @JsonProperty("detailLevel") ImageContent.DetailLevel detailLevel) {
    }
}
