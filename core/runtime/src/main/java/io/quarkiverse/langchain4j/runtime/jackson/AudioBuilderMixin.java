package io.quarkiverse.langchain4j.runtime.jackson;

import dev.langchain4j.data.audio.Audio;
import io.quarkus.jackson.JacksonMixin;
import tools.jackson.databind.annotation.JsonPOJOBuilder;

@JacksonMixin(Audio.Builder.class)
@JsonPOJOBuilder(withPrefix = "")
public abstract class AudioBuilderMixin {
}
