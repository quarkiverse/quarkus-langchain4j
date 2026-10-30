package io.quarkiverse.langchain4j.runtime.jackson;

import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import dev.langchain4j.data.video.Video;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(Video.Builder.class)
@JsonPOJOBuilder(withPrefix = "")
public abstract class VideoBuilderMixin {
}
