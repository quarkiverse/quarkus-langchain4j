package io.quarkiverse.langchain4j.runtime.jackson;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import dev.langchain4j.data.message.VideoContent;
import dev.langchain4j.data.video.Video;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(VideoContent.class)
public abstract class VideoContentMixin {

    @JsonCreator
    public VideoContentMixin(@JsonProperty("video") Video video) {
    }
}
