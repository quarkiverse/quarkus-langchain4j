package io.quarkiverse.langchain4j.runtime.jackson;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.data.message.AudioContent;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(AudioContent.class)
public abstract class AudioContentMixin {

    @JsonCreator
    public AudioContentMixin(@JsonProperty("audio") Audio audio) {
    }
}
