package io.quarkiverse.langchain4j.runtime.jackson;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import dev.langchain4j.data.video.Video;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(Video.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonDeserialize(builder = Video.Builder.class)
public abstract class VideoMixin {

}
