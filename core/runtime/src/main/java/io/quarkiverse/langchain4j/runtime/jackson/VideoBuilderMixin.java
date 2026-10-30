package io.quarkiverse.langchain4j.runtime.jackson;

import dev.langchain4j.data.video.Video;
import io.quarkus.jackson.JacksonMixin;
import tools.jackson.databind.annotation.JsonPOJOBuilder;

@JacksonMixin(Video.Builder.class)
@JsonPOJOBuilder(withPrefix = "")
public abstract class VideoBuilderMixin {
}
