package io.quarkiverse.langchain4j.runtime.jackson;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import dev.langchain4j.data.audio.Audio;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(Audio.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonDeserialize(builder = Audio.Builder.class)
public abstract class AudioMixin {

}
