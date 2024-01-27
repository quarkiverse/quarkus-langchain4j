package io.quarkiverse.langchain4j.openai.runtime.jackson;

import java.net.URI;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import dev.ai4j.openai4j.image.GenerateImagesResponse;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(GenerateImagesResponse.ImageData.class)
public abstract class ImageDataMixin {

    @JsonCreator
    public ImageDataMixin(@JsonProperty("url") URI url, @JsonProperty("b64_json") String b64Json,
            @JsonProperty("revised_prompt") String revisedPrompt) {

    }
}
