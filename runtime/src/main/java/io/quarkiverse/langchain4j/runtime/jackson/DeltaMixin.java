package io.quarkiverse.langchain4j.runtime.jackson;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import dev.ai4j.openai4j.chat.Delta;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(Delta.class)
@JsonDeserialize(builder = Delta.Builder.class)
public abstract class DeltaMixin {
}
