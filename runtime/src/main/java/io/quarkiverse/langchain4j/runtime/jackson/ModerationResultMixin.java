package io.quarkiverse.langchain4j.runtime.jackson;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import dev.ai4j.openai4j.moderation.ModerationResult;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(ModerationResult.class)
@JsonDeserialize(builder = ModerationResult.Builder.class)
public abstract class ModerationResultMixin {
}
