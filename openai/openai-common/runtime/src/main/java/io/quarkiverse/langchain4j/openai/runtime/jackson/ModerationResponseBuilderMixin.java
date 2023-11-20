package io.quarkiverse.langchain4j.openai.runtime.jackson;

import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import dev.ai4j.openai4j.moderation.ModerationResponse;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(ModerationResponse.Builder.class)
@JsonPOJOBuilder(withPrefix = "")
public abstract class ModerationResponseBuilderMixin {
}
