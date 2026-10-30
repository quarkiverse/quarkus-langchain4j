package io.quarkiverse.langchain4j.runtime.jackson;

import com.fasterxml.jackson.annotation.JsonInclude;

import dev.langchain4j.data.video.Video;
import io.quarkus.jackson.JacksonMixin;
import tools.jackson.databind.annotation.JsonDeserialize;

@JacksonMixin(Video.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonDeserialize(builder = Video.Builder.class)
public abstract class VideoMixin {

}
