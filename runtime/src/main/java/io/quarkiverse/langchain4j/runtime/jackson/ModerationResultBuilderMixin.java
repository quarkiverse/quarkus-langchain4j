package io.quarkiverse.langchain4j.runtime.jackson;

import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import dev.ai4j.openai4j.moderation.ModerationResult;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(ModerationResult.Builder.class)
@JsonPOJOBuilder(withPrefix = "")
public abstract class ModerationResultBuilderMixin {
}
