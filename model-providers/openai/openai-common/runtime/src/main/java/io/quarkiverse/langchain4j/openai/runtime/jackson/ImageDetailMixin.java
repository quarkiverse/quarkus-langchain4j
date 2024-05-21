package io.quarkiverse.langchain4j.openai.runtime.jackson;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import dev.ai4j.openai4j.chat.ImageDetail;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(ImageDetail.class)
@JsonSerialize(using = ImageDetailsSerializer.class)
public abstract class ImageDetailMixin {

}
