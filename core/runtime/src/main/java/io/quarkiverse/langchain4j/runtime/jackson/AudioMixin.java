package io.quarkiverse.langchain4j.runtime.jackson;

import com.fasterxml.jackson.annotation.JsonInclude;

import dev.langchain4j.data.audio.Audio;
import io.quarkus.jackson.JacksonMixin;
import tools.jackson.databind.annotation.JsonDeserialize;

@JacksonMixin(Audio.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonDeserialize(builder = Audio.Builder.class)
public abstract class AudioMixin {

}
