package io.quarkiverse.langchain4j.runtime.jackson;

import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import dev.langchain4j.data.audio.Audio;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(Audio.Builder.class)
@JsonPOJOBuilder(withPrefix = "")
public abstract class AudioBuilderMixin {
}
